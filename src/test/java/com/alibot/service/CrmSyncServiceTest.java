package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.alibot.domain.CrmSyncQueueItem;
import com.alibot.domain.CrmSyncStatus;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.CrmSyncQueueRepository;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.exception.ForbiddenException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * ТЗ п.85-87 — CRMAdapter. app.crm.webhook-url задан только в этом тесте (через
 * TestPropertySource), поэтому активируется реальный HttpCrmSyncGateway + бин RestTemplate
 * (см. CrmHttpClientConfig), к которому привязывается MockRestServiceServer — это проверка
 * настоящего HTTP-вызова, а не мока собственного сервиса, тем же принципом, что и остальные
 * "проверено вживую" части этой сессии (docker/Postgres backup-restore, initData HMAC и т.п.).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.crm.webhook-url=http://test-crm.local/webhook",
        "app.crm.webhook-secret=s3cr3t",
        "app.crm.max-attempts=3"
})
class CrmSyncServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private CrmSyncService crmSyncService;
    @Autowired
    private CrmSyncQueueRepository queueRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;
    @Autowired
    private RestTemplate crmRestTemplate;

    private MockRestServiceServer server() {
        return MockRestServiceServer.bindTo(crmRestTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void orderCreationEnqueuesAndSuccessfulDeliveryMarksSent() {
        MockRestServiceServer mockServer = server();
        var actors = createAdminAndMaster(601L, 602L);

        mockServer.expect(requestTo("http://test-crm.local/webhook"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Event-Type", "ORDER_CREATED"))
                .andExpect(header("X-Crm-Secret", "s3cr3t"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var order = orderService.create(sampleOrderCommand(actors.master().getId()), actors.admin());

        List<CrmSyncQueueItem> queued = queueRepository.findAll().stream()
                .filter(i -> i.getOrderId().equals(order.getId())).toList();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getEventType()).isEqualTo("ORDER_CREATED");

        crmSyncService.attemptDelivery(queued.get(0).getId());
        mockServer.verify();

        CrmSyncQueueItem reloaded = queueRepository.findById(queued.get(0).getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CrmSyncStatus.SENT);
    }

    @Test
    void failedDeliveryBacksOffThenManualRetrySucceeds() {
        MockRestServiceServer mockServer = server();
        var actors = createAdminAndMaster(603L, 604L);

        mockServer.expect(requestTo("http://test-crm.local/webhook")).andRespond(withServerError());

        var order = orderService.create(sampleOrderCommand(actors.master().getId()), actors.admin());
        CrmSyncQueueItem item = queueRepository.findAll().stream()
                .filter(i -> i.getOrderId().equals(order.getId())).findFirst().orElseThrow();

        crmSyncService.attemptDelivery(item.getId());
        mockServer.verify();

        CrmSyncQueueItem afterFailure = queueRepository.findById(item.getId()).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(CrmSyncStatus.PENDING);
        assertThat(afterFailure.getAttempts()).isEqualTo(1);
        assertThat(afterFailure.getNextAttemptAt()).isAfter(Instant.now());
        // Планировщик не должен подхватить запись раньше времени следующей попытки.
        assertThat(crmSyncService.pendingIds(Instant.now())).doesNotContain(item.getId());

        // Ручной повтор (ТЗ п.87) сбрасывает время ожидания немедленно.
        crmSyncService.retry(item.getId(), actors.admin());
        CrmSyncQueueItem afterRetryReset = queueRepository.findById(item.getId()).orElseThrow();
        assertThat(afterRetryReset.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now().plus(1, ChronoUnit.SECONDS));

        MockRestServiceServer secondAttempt = server();
        secondAttempt.expect(requestTo("http://test-crm.local/webhook")).andRespond(withSuccess());
        crmSyncService.attemptDelivery(item.getId());
        secondAttempt.verify();

        assertThat(queueRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(CrmSyncStatus.SENT);
    }

    @Test
    void exhaustingAttemptsMarksFailedVisibleOnlyToAdmin() {
        MockRestServiceServer mockServer = server();
        var actors = createAdminAndMaster(605L, 606L);
        mockServer.expect(requestTo("http://test-crm.local/webhook")).andRespond(withServerError());
        var order = orderService.create(sampleOrderCommand(actors.master().getId()), actors.admin());
        CrmSyncQueueItem item = queueRepository.findAll().stream()
                .filter(i -> i.getOrderId().equals(order.getId())).findFirst().orElseThrow();

        for (int i = 0; i < 3; i++) {
            MockRestServiceServer attempt = server();
            attempt.expect(requestTo("http://test-crm.local/webhook")).andRespond(withServerError());
            crmSyncService.attemptDelivery(item.getId());
            attempt.verify();
        }

        CrmSyncQueueItem failed = queueRepository.findById(item.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(CrmSyncStatus.FAILED);

        assertThat(crmSyncService.listFailed(actors.admin())).extracting(CrmSyncQueueItem::getId).contains(item.getId());
        assertThatThrownBy(() -> crmSyncService.listFailed(actors.masterActor())).isInstanceOf(ForbiddenException.class);
    }

    private CreateOrderCommand sampleOrderCommand(UUID masterId) {
        return new CreateOrderCommand(
                "Клиент", "+79990009999", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                masterId, null, null, null, "test");
    }

    private Actors createAdminAndMaster(long adminTelegramId, long masterTelegramId) {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(adminTelegramId).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(masterTelegramId).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(com.alibot.domain.CommissionType.MANUAL).active(true).build());

        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), adminTelegramId, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), masterTelegramId, Role.MASTER, master.getId());
        return new Actors(admin, masterActor, master);
    }

    private record Actors(AuthenticatedActor admin, AuthenticatedActor masterActor, Master master) {
    }
}
