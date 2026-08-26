package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.PriceApprovalCommand;
import com.alibot.service.dto.WorkReportCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Прогоняет полный цикл заказа через тот же слой сервисов, которым пользуются и бот, и REST API:
 * создание -> назначение -> принятие -> выезд -> диагностика -> согласование цены -> ремонт ->
 * отчёт -> оплата. Проверяет, что OrderStatusMachine и денежные формулы работают согласованно
 * end-to-end, а не только в изоляции.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderLifecycleIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WorkReportService workReportService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    @Test
    void fullHappyPathReachesPaid() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(1001L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(2002L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Ахмед").status(com.alibot.domain.MasterStatus.ACTIVE)
                .commissionType(CommissionType.PERCENT).commissionValue(BigDecimal.TEN).active(true).build());

        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 1001L, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), 2002L, Role.MASTER, master.getId());

        CreateOrderCommand createCmd = new CreateOrderCommand(
                "Иван", "+79991234567", "Холодильник", "Liebherr", "CNef 4815",
                "Не морозит", null, "Москва, ул. Тестовая, 1",
                LocalDate.now().plusDays(1), LocalTime.of(15, 0), LocalTime.of(18, 0),
                master.getId(), null, null, null, "test");

        Order order = orderService.create(createCmd, admin);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);

        order = orderService.acceptByMaster(order.getId(), masterActor);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

        order = orderService.markOnTheWay(order.getId(), masterActor);
        order = orderService.markArrived(order.getId(), masterActor);
        order = orderService.startDiagnostics(order.getId(), masterActor);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DIAGNOSTICS);

        order = orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Неисправен компрессор", "Замена компрессора",
                        BigDecimal.valueOf(5000), BigDecimal.valueOf(4000)), masterActor);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PRICE_APPROVAL);
        assertThat(order.getEstimatedPrice()).isEqualByComparingTo("9000");

        order = orderService.approvePriceByCustomer(order.getId(), masterActor);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        var report = workReportService.submit(order.getId(),
                new WorkReportCommand("Заменён компрессор", BigDecimal.valueOf(5000),
                        BigDecimal.valueOf(4000), BigDecimal.valueOf(2500), null, null),
                masterActor);
        assertThat(report.getFinalPrice()).isEqualByComparingTo("9000");
        assertThat(report.getMasterPayout()).isEqualByComparingTo("900"); // 10% от 9000

        order = orderService.getById(order.getId(), admin);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        paymentService.payFull(order.getId(), admin);
        order = orderService.getById(order.getId(), admin);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.amountDue()).isEqualByComparingTo("0");
    }
}
