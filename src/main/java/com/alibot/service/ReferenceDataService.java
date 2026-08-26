package com.alibot.service;

import com.alibot.domain.ReferenceCategory;
import com.alibot.domain.ReferenceItem;
import com.alibot.repository.ReferenceItemRepository;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ТЗ п.18.1/132 — справочники, редактируемые через интерфейс SUPERADMIN, а не только правкой
 * конфига. Геттеры (getApplianceTypes и т.п.) намеренно повторяют сигнатуры бывшего
 * CatalogProperties — CreateOrderWizard/MiscWizards переключены на этот сервис почти без правок
 * в местах вызова, меняется только источник данных (БД вместо статичного конфига).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final ReferenceItemRepository repository;
    private final AccessControlService accessControl;

    public List<String> getApplianceTypes() {
        return values(ReferenceCategory.APPLIANCE_TYPE);
    }

    public List<String> getPopularBrands() {
        return values(ReferenceCategory.BRAND);
    }

    public List<String> getTimeSlots() {
        return values(ReferenceCategory.TIME_SLOT);
    }

    public List<String> getMasterDeclineReasons() {
        return values(ReferenceCategory.MASTER_DECLINE_REASON);
    }

    public List<String> getCustomerCancelReasons() {
        return values(ReferenceCategory.CUSTOMER_CANCEL_REASON);
    }

    public List<String> getRescheduleReasons() {
        return values(ReferenceCategory.RESCHEDULE_REASON);
    }

    private List<String> values(ReferenceCategory category) {
        return repository.findByCategoryAndActiveTrueOrderBySortOrderAscValueAsc(category).stream()
                .map(ReferenceItem::getValue)
                .toList();
    }

    // --- Управление (только SUPERADMIN) ---

    public List<ReferenceItem> listAll(AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        return repository.findAllByOrderByCategoryAscSortOrderAscValueAsc();
    }

    @Transactional
    public ReferenceItem create(ReferenceCategory category, String value, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        if (value == null || value.isBlank()) {
            throw new ValidationException("Значение не может быть пустым");
        }
        if (repository.existsByCategoryAndValueIgnoreCase(category, value)) {
            throw new ValidationException("Такое значение уже есть в этом справочнике");
        }
        int nextOrder = repository.findByCategoryOrderBySortOrderAscValueAsc(category).stream()
                .mapToInt(ReferenceItem::getSortOrder).max().orElse(-1) + 1;
        return repository.save(ReferenceItem.builder()
                .category(category).value(value.trim()).sortOrder(nextOrder).active(true).build());
    }

    @Transactional
    public ReferenceItem update(UUID id, String value, Boolean active, Integer sortOrder, AuthenticatedActor actor) {
        accessControl.assertIsSuperAdmin(actor);
        ReferenceItem item = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пункт справочника " + id + " не найден"));
        if (value != null && !value.isBlank()) {
            item.setValue(value.trim());
        }
        if (active != null) {
            item.setActive(active);
        }
        if (sortOrder != null) {
            item.setSortOrder(sortOrder);
        }
        return repository.save(item);
    }
}
