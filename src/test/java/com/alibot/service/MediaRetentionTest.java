package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.alibot.service.dto.MediaUploadCommand;
import com.alibot.service.exception.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * ТЗ п.100 — срок хранения медиа. app.media.retention-days=0 по умолчанию (см. application.yml —
 * удаление необратимо, включается явно), поэтому здесь оно включено через TestPropertySource
 * только для этого теста. Проверяем реальное удаление файла с диска, а не только пометку в БД.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.media.retention-days=7")
class MediaRetentionTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private MediaService mediaService;
    @Autowired
    private OrderMediaRepository mediaRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void purgeDeletesExpiredFileButKeepsHistoryRecordAndLeavesFreshOneAlone() throws Exception {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(1101L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(1102L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Мастер").status(MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());
        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 1101L, Role.ADMIN, null);
        AuthenticatedActor masterActor = new AuthenticatedActor(masterUser.getId(), 1102L, Role.MASTER, master.getId());

        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990007777", "Холодильник", null, null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                master.getId(), null, null, null, "test");
        Order order = orderService.create(cmd, admin);

        MediaUploadCommand uploadCmd = new MediaUploadCommand(MediaType.PHOTO, MediaStage.BEFORE,
                "tg-file-1", "tg-unique-1", "image/jpeg", "photo.jpg", 100, null, null, null,
                "test-content".getBytes());
        OrderMedia old = mediaService.upload(order.getId(), uploadCmd, masterActor);
        OrderMedia fresh = mediaService.upload(order.getId(),
                new MediaUploadCommand(MediaType.PHOTO, MediaStage.BEFORE, "tg-file-2", "tg-unique-2",
                        "image/jpeg", "photo2.jpg", 100, null, null, null, "test-content-2".getBytes()),
                masterActor);

        Path oldFilePath = Path.of("./build/test-media", old.getStoragePath());
        assertThat(Files.exists(oldFilePath)).isTrue();

        // "Состарим" только первую запись — 10 дней назад, retention 7 дней.
        jdbcTemplate.update("update order_media set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), old.getId());

        mediaService.purgeExpired();

        assertThat(Files.exists(oldFilePath)).isFalse();
        OrderMedia purgedReloaded = mediaRepository.findById(old.getId()).orElseThrow();
        assertThat(purgedReloaded.getPurgedAt()).isNotNull();
        assertThatThrownBy(() -> mediaService.readContent(purgedReloaded)).isInstanceOf(NotFoundException.class);

        // Свежая запись не тронута.
        OrderMedia freshReloaded = mediaRepository.findById(fresh.getId()).orElseThrow();
        assertThat(freshReloaded.getPurgedAt()).isNull();
        assertThat(mediaService.readContent(freshReloaded)).isEqualTo("test-content-2".getBytes());
    }
}
