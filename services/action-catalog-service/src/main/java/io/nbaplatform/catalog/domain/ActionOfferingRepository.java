package io.nbaplatform.catalog.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionOfferingRepository extends JpaRepository<ActionOffering, String> {

    Optional<ActionOffering> findByTenantIdAndId(String tenantId, String id);

    Page<ActionOffering> findByTenantId(String tenantId, Pageable pageable);

    Page<ActionOffering> findByTenantIdAndLifecycleStatus(String tenantId, String lifecycleStatus, Pageable pageable);
}
