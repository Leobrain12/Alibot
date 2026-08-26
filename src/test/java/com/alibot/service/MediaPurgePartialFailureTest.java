package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.MediaStage;
import com.alibot.domain.MediaType;
import com.alibot.domain.Order;
import com.alibot.domain.OrderMedia;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.OrderMediaRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.storage.MediaStorage;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Регрессия на баг, найденный при повторном аудите: раньше purgeExpired() обрабатывал весь
 * пакет в одной транзакции — если удаление файла N бросало исключение, файлы 1..N-1 уже были
 * физически стёрты с диска (это не откатывается транзакцией БД), а их OrderMedia.purgedAt
 * откатывался обратно к null — БД врала бы, что файл ещё существует. Теперь каждая запись — своя
 * транзакция (MediaPurgeExecutor), сбой одной не трогает уже зафиксированные соседние.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.media.retention-days=7")
class MediaPurgePartialFailureTest {

    @Autowired
    private MediaService mediaService;
    @Autowired
    private OrderMediaRepository mediaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    @MockBean
    private MediaStorage mediaStorage;

    @Test
    void oneFailingDeleteDoesNotRollBackAlreadySucceededSiblings() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(1201L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(1202L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());
        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 1201L, Role.ADMIN, null);
        Order order = orderService.create(new CreateOrderCommand(
                "Клиент", "+79990008888", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                master.getId(), null, null, null, "test"), admin);

        OrderMedia good1 = insertExpiredMedia(order.getId(), adminUser.getId(), "good1/photo.jpg");
        OrderMedia bad = insertExpiredMedia(order.getId(), adminUser.getId(), "bad/photo.jpg");
        OrderMedia good2 = insertExpiredMedia(order.getId(), adminUser.getId(), "good2/photo.jpg");

        doNothing().when(mediaStorage).delete(eq("good1/photo.jpg"));
        doThrow(new RuntimeException("simulated disk failure")).when(mediaStorage).delete(eq("bad/photo.jpg"));
        doNothing().when(mediaStorage).delete(eq("good2/photo.jpg"));

        mediaService.purgeExpired();

        assertThat(mediaRepository.findById(good1.getId()).orElseThrow().getPurgedAt()).isNotNull();
        assertThat(mediaRepository.findById(good2.getId()).orElseThrow().getPurgedAt()).isNotNull();
        // Провалившаяся запись осталась непомеченной — не потеряна, подхватится следующим прогоном.
        assertThat(mediaRepository.findById(bad.getId()).orElseThrow().getPurgedAt()).isNull();
    }

    private OrderMedia insertExpiredMedia(UUID orderId, UUID uploadedBy, String storagePath) {
        OrderMedia media = OrderMedia.builder()
                .orderId(orderId)
                .uploadedByUserId(uploadedBy)
                .mediaType(MediaType.PHOTO)
                .stage(MediaStage.BEFORE)
                .telegramFileId("tg-" + storagePath)
                .storagePath(storagePath)
                .build();
        media = mediaRepository.save(media);
        jdbcTemplate.update("update order_media set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), media.getId());
        return media;
    }
}
