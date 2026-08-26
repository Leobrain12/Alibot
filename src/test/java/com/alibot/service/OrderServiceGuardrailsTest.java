package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.PriceApprovalCommand;
import com.alibot.service.dto.WorkReportCommand;
import com.alibot.service.exception.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Регрессионные тесты на баги, найденные полным аудитом кодовой базы (см. отчёт пользователю):
 * до фикса generic /transition пропускал ЛЮБОЙ targetStatus, включая COMPLETED — минуя
 * обязательный WorkReport (final_price/masterPayout навсегда оставались null, заказ становился
 * неоплачиваемым); changeMaster не проверял OrderStatusMachine вовсе и тихо реоткрывал уже
 * завершённые заказы, стирая их финальное состояние.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceGuardrailsTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WorkReportService workReportService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    private record Actors(AuthenticatedActor admin, AuthenticatedActor master, Master masterEntity) {
    }

    private Actors createAdminAndMaster(long adminTelegramId, long masterTelegramId) {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(adminTelegramId).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(masterTelegramId).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());
        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), adminTelegramId, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), masterTelegramId, Role.MASTER, master.getId());
        return new Actors(admin, masterActor, master);
    }

    private Order createAssignedOrder(Actors actors, long phoneSuffix) {
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+7999000" + String.format("%04d", phoneSuffix), "Холодильник", null, null,
                "Не морозит", null, "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                actors.masterEntity().getId(), null, null, null, "test");
        return orderService.create(cmd, actors.admin());
    }

    @Test
    void genericTransitionRejectsCompletedInsteadOfBypassingWorkReport() {
        Actors actors = createAdminAndMaster(701L, 702L);
        Order order = createAssignedOrder(actors, 1);
        order = orderService.acceptByMaster(order.getId(), actors.master());
        order = orderService.markOnTheWay(order.getId(), actors.master());
        order = orderService.markArrived(order.getId(), actors.master());
        order = orderService.startDiagnostics(order.getId(), actors.master());
        order = orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Причина", "Работы", BigDecimal.valueOf(1000), BigDecimal.ZERO), actors.master());
        order = orderService.approvePriceByCustomer(order.getId(), actors.master());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        var orderId = order.getId();
        assertThatThrownBy(() -> orderService.transitionSimple(orderId, OrderStatus.COMPLETED, actors.master(), null))
                .isInstanceOf(ValidationException.class);

        // finalPrice уже не null здесь: approvePriceByCustomer сам предзаполняет его согласованной
        // клиентом суммой (order.setFinalPrice(estimatedPrice)) — реальный сигнал того, что
        // WorkReport не был обойдён, это completedAt/masterPayout, которые проставляет только он.
        Order reloaded = orderService.getById(order.getId(), actors.admin());
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(reloaded.getCompletedAt()).isNull();
        assertThat(reloaded.getMasterPayout()).isNull();
    }

    @Test
    void changeMasterRejectsOnCompletedOrder() {
        Actors actors = createAdminAndMaster(703L, 704L);
        Actors otherMaster = createAdminAndMaster(705L, 706L);
        Order order = createAssignedOrder(actors, 2);
        order = orderService.acceptByMaster(order.getId(), actors.master());
        order = orderService.markOnTheWay(order.getId(), actors.master());
        order = orderService.markArrived(order.getId(), actors.master());
        order = orderService.startDiagnostics(order.getId(), actors.master());
        order = orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Причина", "Работы", BigDecimal.valueOf(1000), BigDecimal.ZERO), actors.master());
        order = orderService.approvePriceByCustomer(order.getId(), actors.master());
        workReportService.submit(order.getId(),
                new WorkReportCommand("Готово", BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500), null),
                actors.master());

        Order completed = orderService.getById(order.getId(), actors.admin());
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completed.getFinalPrice()).isEqualByComparingTo("1000");

        var orderId = completed.getId();
        var newMasterId = otherMaster.masterEntity().getId();
        assertThatThrownBy(() -> orderService.changeMaster(orderId, newMasterId, actors.admin()))
                .isInstanceOf(ValidationException.class);

        Order stillCompleted = orderService.getById(order.getId(), actors.admin());
        assertThat(stillCompleted.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stillCompleted.getFinalPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void createRejectsPhoneWithTooFewDigitsThroughServiceNotJustBot() {
        Actors actors = createAdminAndMaster(707L, 708L);
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "123", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                null, null, null, null, "test");
        assertThatThrownBy(() -> orderService.create(cmd, actors.admin())).isInstanceOf(ValidationException.class);
    }
}
