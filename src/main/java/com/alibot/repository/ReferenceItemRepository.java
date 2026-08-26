package com.alibot.repository;

import com.alibot.domain.ReferenceCategory;
import com.alibot.domain.ReferenceItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceItemRepository extends JpaRepository<ReferenceItem, UUID> {
    List<ReferenceItem> findByCategoryAndActiveTrueOrderBySortOrderAscValueAsc(ReferenceCategory category);

    List<ReferenceItem> findAllByOrderByCategoryAscSortOrderAscValueAsc();

    List<ReferenceItem> findByCategoryOrderBySortOrderAscValueAsc(ReferenceCategory category);

    boolean existsByCategoryAndValueIgnoreCase(ReferenceCategory category, String value);
}
