package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.PaymentCommand;
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
 * Пробелы в валидации, найденные при целевом ревью ("улучши валидацию данных"): финансовые поля
 * нигде не проверялись на отрицательность (мастер мог занизить выручку отрицательной ценой),
 * оплата не проверялась на превышение остатка долга, комиссия FIXED/PERCENT могла быть создана
 * без значения (WorkReportService молча платил бы 0), telegram_user_id мог быть <= 0 (коллизия
 * с зарезервированным id системного актора -1, см. SystemActorBootstrap).
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationImprovementsTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WorkReportService workReportService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private UserManagementService userManagementService;
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

    @Test
    void createRejectsBackwardsTimeSlot() {
        Actors actors = createAdminAndMaster(1301L, 1302L);
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990009991", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(18, 0), LocalTime.of(9, 0),
                null, null, null, null, "test");
        assertThatThrownBy(() -> orderService.create(cmd, actors.admin())).isInstanceOf(ValidationException.class);
    }

    @Test
    void priceApprovalRejectsNegativeLaborPrice() {
        Actors actors = createAdminAndMaster(1303L, 1304L);
        Order order = fullyDispatchedOrder(actors, 1);
        assertThatThrownBy(() -> orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Причина", "Работы", BigDecimal.valueOf(-500), BigDecimal.ZERO), actors.master()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void workReportRejectsNegativeMasterPayout() {
        Actors actors = createAdminAndMaster(1305L, 1306L);
        Order order = fullyDispatchedOrder(actors, 2);
        order = orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Причина", "Работы", BigDecimal.valueOf(1000), BigDecimal.ZERO), actors.master());
        order = orderService.approvePriceByCustomer(order.getId(), actors.master());
        var orderId = order.getId();
        assertThatThrownBy(() -> workReportService.submit(orderId,
                new WorkReportCommand("Готово", BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(-100), null),
                actors.master()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void paymentRejectsAmountAboveRemainingDue() {
        Actors actors = createAdminAndMaster(1307L, 1308L);
        Order order = fullyDispatchedOrder(actors, 3);
        order = orderService.startPriceApproval(order.getId(),
                new PriceApprovalCommand("Причина", "Работы", BigDecimal.valueOf(1000), BigDecimal.ZERO), actors.master());
        order = orderService.approvePriceByCustomer(order.getId(), actors.master());
        workReportService.submit(order.getId(),
                new WorkReportCommand("Готово", BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(200), null),
                actors.master());
        var orderId = order.getId();
        var admin = actors.admin();
        assertThatThrownBy(() -> paymentService.registerPayment(orderId, new PaymentCommand(BigDecimal.valueOf(5000), "CASH"), admin))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void masterProfileRejectsFixedCommissionWithoutValue() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(1309L).role(Role.SUPERADMIN).name("Super").active(true).build());
        AuthenticatedActor superadmin = new AuthenticatedActor(adminUser.getId(), 1309L, Role.SUPERADMIN, null);
        User masterUser = userManagementService.createUser(1310L, "Новый мастер", null, Role.MASTER, superadmin);
        assertThatThrownBy(() -> userManagementService.createMasterProfile(
                masterUser.getId(), "Новый мастер", null, CommissionType.FIXED, null, superadmin))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void masterProfileRejectsPercentAbove100() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(1311L).role(Role.SUPERADMIN).name("Super").active(true).build());
        AuthenticatedActor superadmin = new AuthenticatedActor(adminUser.getId(), 1311L, Role.SUPERADMIN, null);
        User masterUser = userManagementService.createUser(1312L, "Новый мастер 2", null, Role.MASTER, superadmin);
        assertThatThrownBy(() -> userManagementService.createMasterProfile(
                masterUser.getId(), "Новый мастер 2", null, CommissionType.PERCENT, BigDecimal.valueOf(150), superadmin))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createUserRejectsNonPositiveTelegramId() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(1313L).role(Role.SUPERADMIN).name("Super").active(true).build());
        AuthenticatedActor superadmin = new AuthenticatedActor(adminUser.getId(), 1313L, Role.SUPERADMIN, null);
        assertThatThrownBy(() -> userManagementService.createUser(-1L, "Кто-то", null, Role.ADMIN, superadmin))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> userManagementService.createUser(0L, "Кто-то", null, Role.ADMIN, superadmin))
                .isInstanceOf(ValidationException.class);
    }

    private Order fullyDispatchedOrder(Actors actors, long phoneSuffix) {
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+7999000" + String.format("%04d", phoneSuffix), "Холодильник", null, null,
                "Не морозит", null, "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                actors.masterEntity().getId(), null, null, null, "test");
        Order order = orderService.create(cmd, actors.admin());
        order = orderService.acceptByMaster(order.getId(), actors.master());
        order = orderService.markOnTheWay(order.getId(), actors.master());
        order = orderService.markArrived(order.getId(), actors.master());
        return orderService.startDiagnostics(order.getId(), actors.master());
    }
}
