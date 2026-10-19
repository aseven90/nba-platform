package io.nbaplatform.interaction.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, String> {

    Optional<Interaction> findByTenantIdAndId(String tenantId, String id);

    Optional<Interaction> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Page<Interaction> findByTenantId(String tenantId, Pageable pageable);

    Page<Interaction> findByTenantIdAndPartyId(String tenantId, String partyId, Pageable pageable);
}
