package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatusHistory;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.OrderRepository;
import com.alibot.repository.OrderStatusHistoryRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.83/84 — напоминание и эскалация неподтверждённой заявки не должны срабатывать
 * повторно на каждый тик планировщика (проверяется полями reminder_sent_at /
 * accept_timeout_notified_at, добавленными миграцией V2).
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderTimersSchedulerTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderTimersScheduler scheduler;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderStatusHistoryRepository historyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private NotificationGateway notifications;

    @Test
    void acceptTimeoutFiresOnceThenStaysQuiet() {
        var actors = createAdminAndMaster(101L, 202L);

        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990000000", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                actors.master().getId(), null, null, null, "test");
        Order order = orderService.create(cmd, actors.admin());

        // "состарим" запись истории ASSIGNED, чтобы имитировать, что заявка висит дольше accept-timeout.
        // created_at у OrderStatusHistory сделан updatable=false (история не редактируется через
        // JPA — ТЗ п.16), поэтому backdate делаем сырым SQL в обход Hibernate.
        OrderStatusHistory lastChange = historyRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow();
        jdbcTemplate.update("update order_status_history set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(15, ChronoUnit.MINUTES)), lastChange.getId());

        scheduler.checkAcceptTimeouts();
        verify(notifications, times(1)).masterNotAcceptedTimeout(any(), anyInt());

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getAcceptTimeoutNotifiedAt()).isNotNull();

        scheduler.checkAcceptTimeouts();
        verify(notifications, times(1)).masterNotAcceptedTimeout(any(), anyInt()); // не второй раз
    }

    @Test
    void reminderFiresOnceWithinWindowThenStaysQuiet() {
        var actors = createAdminAndMaster(103L, 204L);

        // LocalDate.now() + LocalTime.now().plusMinutes(N) отдельно друг от друга рассинхронизируются
        // при переходе через полночь (время оборачивается на 00:xx, а дата остаётся "сегодня") —
        // берём дату и время из одного and того же LocalDateTime, чтобы тест не был хрупким по часам.
        java.time.LocalDateTime visitMoment = java.time.LocalDateTime.now().plusMinutes(30);
        java.time.LocalDateTime visitEnd = java.time.LocalDateTime.now().plusMinutes(90);
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент2", "+79990000001", "Стиральная машина", null, null, "Не сливает", null,
                "Адрес 2", visitMoment.toLocalDate(), visitMoment.toLocalTime(), visitEnd.toLocalTime(),
                actors.master().getId(), null, null, null, "test");
        Order order = orderService.create(cmd, actors.admin());
        order = orderService.acceptByMaster(order.getId(), actors.masterActor());
        assertThat(order.getStatus().name()).isEqualTo("ACCEPTED");

        scheduler.checkReminders();
        verify(notifications, times(1)).reminder(any(), anyInt());

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getReminderSentAt()).isNotNull();

        scheduler.checkReminders();
        verify(notifications, times(1)).reminder(any(), anyInt()); // не второй раз
    }

    private Actors createAdminAndMaster(long adminTelegramId, long masterTelegramId) {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(adminTelegramId).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(masterTelegramId).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(com.alibot.domain.MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());

        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), adminTelegramId, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), masterTelegramId, Role.MASTER, master.getId());
        return new Actors(admin, masterActor, master);
    }

    private record Actors(AuthenticatedActor admin, AuthenticatedActor masterActor, Master master) {
    }
}
