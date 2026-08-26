package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.exception.ForbiddenException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.95 — общий журнал действий. Проверяем, что реальный флоу (создание заказа, назначение
 * мастера) пишет ожидаемые записи, и что просмотр журнала недоступен мастеру.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditLogServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    @Test
    void createAndAssignWriteExpectedAuditEntries() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(555001L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(555002L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(com.alibot.domain.CommissionType.MANUAL).active(true).build());

        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 555001L, Role.ADMIN, null);
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990000003", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                null, null, null, null, "test");
        Order order = orderService.create(cmd, admin);
        orderService.assign(order.getId(), master.getId(), admin);

        var entries = auditLogService.forEntity("ORDER", order.getId(), admin);
        assertThat(entries).extracting("action").contains("ORDER_CREATED", "MASTER_ASSIGNED", "STATUS_CHANGED");

        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), 555002L, Role.MASTER, master.getId());
        assertThatThrownBy(() -> auditLogService.forEntity("ORDER", order.getId(), masterActor))
                .isInstanceOf(ForbiddenException.class);
    }
}
