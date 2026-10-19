package io.nbaplatform.campaign.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, String> {

    Optional<Campaign> findByTenantIdAndId(String tenantId, String id);

    Page<Campaign> findByTenantId(String tenantId, Pageable pageable);

    Page<Campaign> findByTenantIdAndLifecycleStatus(String tenantId, String lifecycleStatus, Pageable pageable);
}
