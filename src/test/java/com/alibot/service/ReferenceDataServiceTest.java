package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibot.domain.ReferenceCategory;
import com.alibot.domain.ReferenceItem;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.UserRepository;
import com.alibot.service.exception.ForbiddenException;
import com.alibot.service.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.132 — справочники редактируются через интерфейс SUPERADMIN, а не только правкой конфига.
 * Проверяем: сиды из V3 доступны сразу, доступ ограничен SUPERADMIN, дубликаты отклоняются,
 * скрытый (active=false) пункт пропадает из публичного списка, но не удаляется физически.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceDataServiceTest {

    @Autowired
    private ReferenceDataService referenceDataService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void seedDataIsAvailableThroughPublicGetters() {
        assertThat(referenceDataService.getApplianceTypes()).contains("Холодильник", "Стиральная машина");
        assertThat(referenceDataService.getTimeSlots()).contains("09:00-12:00");
        assertThat(referenceDataService.getMasterDeclineReasons()).last().isEqualTo("Другая причина");
    }

    @Test
    void onlySuperAdminCanManage() {
        AuthenticatedActor admin = new AuthenticatedActor(UUID.randomUUID(), 1L, Role.ADMIN, null);
        assertThatThrownBy(() -> referenceDataService.create(ReferenceCategory.BRAND, "Zanussi", admin))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRejectsDuplicateAndTogglingHidesFromPublicList() {
        User superAdminUser = userRepository.save(User.builder()
                .telegramUserId(777001L).role(Role.SUPERADMIN).name("SA").active(true).build());
        AuthenticatedActor superAdmin = new AuthenticatedActor(superAdminUser.getId(), 777001L, Role.SUPERADMIN, null);

        ReferenceItem created = referenceDataService.create(ReferenceCategory.BRAND, "Zanussi", superAdmin);
        assertThat(referenceDataService.getPopularBrands()).contains("Zanussi");

        assertThatThrownBy(() -> referenceDataService.create(ReferenceCategory.BRAND, "zanussi", superAdmin))
                .isInstanceOf(ValidationException.class);

        referenceDataService.update(created.getId(), null, false, null, superAdmin);
        assertThat(referenceDataService.getPopularBrands()).doesNotContain("Zanussi");

        // список для управления (listAll) по-прежнему видит скрытый пункт — ничего физически не удалено
        assertThat(referenceDataService.listAll(superAdmin))
                .anyMatch(i -> i.getId().equals(created.getId()) && !i.isActive());
    }
}
