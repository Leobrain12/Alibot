package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.alibot.domain.CommissionType;
import com.alibot.domain.ContactResult;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.38 — рекомендация админу закрыть заказ после N недозвонов. Раньше порог считался
 * (ContactAttemptService.exceedsRecommendedLimit), но никуда не выводился — теперь это реальное
 * уведомление, отправляемое ровно один раз в момент пересечения порога.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContactAttemptServiceTest {

    @Autowired
    private ContactAttemptService contactAttemptService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    @MockBean
    private NotificationGateway notifications;

    @Test
    void notifiesAdminOnceExactlyAtThreshold() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(801L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(802L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());
        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 801L, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), 802L, Role.MASTER, master.getId());

        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990001234", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                master.getId(), null, null, null, "test");
        Order order = orderService.create(cmd, admin);

        // app.order.max-contact-attempts по умолчанию 3 (application.yml) — на 1-й и 2-й попытке
        // уведомления быть не должно, на 3-й — должно прийти ровно один раз.
        contactAttemptService.recordAttempt(order.getId(), ContactResult.NO_ANSWER, null, masterActor);
        contactAttemptService.recordAttempt(order.getId(), ContactResult.BUSY, null, masterActor);
        verify(notifications, times(0)).contactAttemptsExceededNotifyAdmin(any(), anyInt());

        contactAttemptService.recordAttempt(order.getId(), ContactResult.NO_ANSWER, null, masterActor);
        verify(notifications, times(1)).contactAttemptsExceededNotifyAdmin(any(), eq(3));

        // 4-я попытка не должна прислать уведомление повторно.
        contactAttemptService.recordAttempt(order.getId(), ContactResult.NO_ANSWER, null, masterActor);
        verify(notifications, times(1)).contactAttemptsExceededNotifyAdmin(any(), anyInt());

        assertThat(contactAttemptService.history(order.getId(), admin)).hasSize(4);
    }
}
