package com.alibot.service;

import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.repository.MasterRepository;
import com.alibot.service.exception.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ТЗ п.9 — список/фильтрация мастеров при ручном назначении (без автоматического распределения). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterService {

    private final MasterRepository masterRepository;
    private final AccessControlService accessControl;

    /** ТЗ п.8.1/F8 "edit/deactivate" — только SUPERADMIN, как и создание мастеров (ТЗ п.5.1). */
    @Transactional
    public Master update(UUID id, MasterStatus status, Boolean active, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        Master master = masterRepository.findByIdWithUser(id)
                .orElseThrow(() -> new NotFoundException("Мастер " + id + " не найден"));
        if (status != null) {
            master.setStatus(status);
        }
        if (active != null) {
            master.setActive(active);
        }
        return masterRepository.save(master);
    }

    public List<Master> list(AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return masterRepository.findAll();
    }

    /** ТЗ п.18.11 — мастера для назначения: активные, по возможности отфильтрованные по технике/бренду/гео. */
    public List<Master> findSuitable(String applianceType, String brand, String geoZone, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return masterRepository.findAll().stream()
                .filter(Master::isAssignable)
                .sorted(Comparator.comparing(m -> matchScore(m, applianceType, brand, geoZone), Comparator.reverseOrder()))
                .toList();
    }

    private int matchScore(Master m, String applianceType, String brand, String geoZone) {
        int score = 0;
        if (applianceType != null && m.getApplianceTypes().stream().anyMatch(applianceType::equalsIgnoreCase)) {
            score += 4;
        }
        if (brand != null && m.getBrands().stream().anyMatch(brand::equalsIgnoreCase)) {
            score += 2;
        }
        if (geoZone != null && m.getGeoZones().stream().anyMatch(geoZone::equalsIgnoreCase)) {
            score += 1;
        }
        return score;
    }

    public Master getById(UUID id, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        return masterRepository.findByIdWithUser(id)
                .orElseThrow(() -> new NotFoundException("Мастер " + id + " не найден"));
    }
}
