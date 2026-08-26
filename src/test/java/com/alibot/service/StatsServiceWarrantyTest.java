package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.PriceApprovalCommand;
import com.alibot.service.dto.WarrantyCommand;
import com.alibot.service.dto.WorkReportCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.65 — раздельная аналитика гарантий (warranty_rate, warranty_cost). Раньше warrantyOrders
 * считался, но rate/cost как отдельных метрик не было вовсе. warranty_cost — это издержки на
 * ЗАПЧАСТИ/ВЫПЛАТУ по самому гарантийному визиту (дочерний заказ с warrantyParentOrderId), а не
 * что-либо на исходном заказе — он уже был оплачен клиентом, доход с него не пересчитывается.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatsServiceWarrantyTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WorkReportService workReportService;
    @Autowired
    private StatsService statsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    @Test
    void warrantyRateAndCostReflectOnlyTheWarrantyChildOrder() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(901L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(902L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());
        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 901L, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), 902L, Role.MASTER, master.getId());

        // overallStats агрегирует по ВСЕЙ базе, а не только по заказам этого теста — в общем
        // прогоне сьюта другие тесты (OrderLifecycleIntegrationTest и т.п.) тоже успевают
        // завершить свои заказы в то же окно времени. Сравниваем дельту до/после, а не
        // абсолютные значения — иначе тест ломается не от своего кода, а от соседей по сьюту.
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        StatsService.OverallStats before = statsService.overallStats(from, to, admin);

        // Исходный заказ доводим до PAID — тот же путь, что и в OrderLifecycleIntegrationTest.
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990005555", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                master.getId(), null, null, null, "test");
        Order order = orderService.create(cmd, admin);
        order = orderService.acceptByMaster(order.getId(), masterActor);
        order = orderService.markOnTheWay(order.getId(), masterActor);
        order = orderService.markArrived(order.getId(), masterActor);
        order = orderService.startDiagnostics(order.getId(), masterActor);
        order = orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Причина", "Работы", BigDecimal.valueOf(1000), BigDecimal.ZERO), masterActor);
        order = orderService.approvePriceByCustomer(order.getId(), masterActor);
        workReportService.submit(order.getId(),
                new WorkReportCommand("Готово", BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(300), null),
                masterActor);

        // Открываем гарантию и полностью проводим дочерний заказ — с реальными partsCost/masterPayout.
        Order warrantyChild = orderService.createWarrantyOrder(
                new WarrantyCommand(order.getId(), "Снова не морозит", LocalDate.now().plusDays(2),
                        LocalTime.of(9, 0), LocalTime.of(11, 0), master.getId(), "По гарантии"),
                admin);
        warrantyChild = orderService.acceptByMaster(warrantyChild.getId(), masterActor);
        warrantyChild = orderService.markOnTheWay(warrantyChild.getId(), masterActor);
        warrantyChild = orderService.markArrived(warrantyChild.getId(), masterActor);
        warrantyChild = orderService.startDiagnostics(warrantyChild.getId(), masterActor);
        warrantyChild = orderService.startPriceApproval(warrantyChild.getId(),
                new PriceApprovalCommand("Гарантийный случай", "Замена детали", BigDecimal.ZERO, BigDecimal.ZERO), masterActor);
        warrantyChild = orderService.approvePriceByCustomer(warrantyChild.getId(), masterActor);
        workReportService.submit(warrantyChild.getId(),
                new WorkReportCommand("Заменена деталь по гарантии", BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.valueOf(750), BigDecimal.valueOf(200), null),
                masterActor);

        StatsService.OverallStats after = statsService.overallStats(from, to, admin);
        // createWarrantyOrder переводит ИСХОДНЫЙ заказ из COMPLETED в WARRANTY_RETURN — он больше
        // не считается completedOrders (status in COMPLETED/PAID), в счёт идёт только гарантийный
        // дочерний заказ: +1 completed, +1 warranty (исходный, теперь WARRANTY_RETURN).
        // Cost — только с дочернего: 750 (запчасти) + 200 (выплата) = 950; на исходном тоже была
        // выплата (300), но она не должна попасть в warranty_cost — это его собственный, уже
        // оплаченный клиентом ремонт, а не гарантийные издержки.
        assertThat(after.completedOrders() - before.completedOrders()).isEqualTo(1);
        assertThat(after.warrantyOrders() - before.warrantyOrders()).isEqualTo(1);
        assertThat(after.warrantyCost().subtract(before.warrantyCost())).isEqualByComparingTo("950");

        // masterStats уже изолирован по masterId (свежесозданный, соседи по сьюту его не трогают) —
        // здесь можно проверять и rate напрямую, не только дельту. Исходный заказ теперь в статусе
        // WARRANTY_RETURN, а не COMPLETED/PAID, поэтому countCompleted видит только дочерний
        // гарантийный заказ: completed=1, warranty=1 (исходный) -> rate = 1/1 = 100%.
        StatsService.MasterStats masterStats = statsService.masterStats(master.getId(), from, to, admin);
        assertThat(masterStats.completedOrders()).isEqualTo(1);
        assertThat(masterStats.warrantyOrders()).isEqualTo(1);
        assertThat(masterStats.warrantyRate()).isEqualByComparingTo("100.0");
        assertThat(masterStats.warrantyCost()).isEqualByComparingTo("950");
    }
}
