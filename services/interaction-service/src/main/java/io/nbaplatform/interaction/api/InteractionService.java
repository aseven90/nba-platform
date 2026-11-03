package io.nbaplatform.interaction.api;

import io.nbaplatform.events.EventTypes;
import io.nbaplatform.events.InteractionRecorded;
import io.nbaplatform.events.Topics;
import io.nbaplatform.interaction.domain.Interaction;
import io.nbaplatform.interaction.domain.InteractionRepository;
import io.nbaplatform.platform.outbox.OutboxWriter;
import io.nbaplatform.platform.web.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {

    static final String BASE_PATH = "/tmf-api/partyInteractionManagement/v4/partyInteraction";

    private final InteractionRepository repository;
    private final OutboxWriter outbox;

    public InteractionService(InteractionRepository repository, OutboxWriter outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Optional<PartyInteraction> findByIdempotencyKey(String tenantId, String key) {
        return key == null ? Optional.empty()
                : repository.findByTenantIdAndIdempotencyKey(tenantId, key).map(InteractionService::toDto);
    }

    @Transactional
    public PartyInteraction record(String tenantId, String idempotencyKey, PartyInteraction request) {
        Interaction interaction = new Interaction(
                UUID.randomUUID().toString(),
                tenantId,
                idempotencyKey,
                request.relatedParty().get(0).id(),
                request.decisionId(),
                request.productOffering().id(),
                request.promotion() == null ? null : request.promotion().id(),
                request.channel().get(0).id(),
                request.outcome(),
                request.interactionDate() == null ? Instant.now() : request.interactionDate());
        repository.saveAndFlush(interaction);
        outbox.append(Topics.INTERACTION_RECORDED, EventTypes.INTERACTION_RECORDED, tenantId, interaction.getPartyId(),
                new InteractionRecorded(interaction.getId(), interaction.getPartyId(), interaction.getDecisionId(),
                        interaction.getOfferingId(), interaction.getCampaignId(), interaction.getChannel(),
                        interaction.getOutcome(), interaction.getInteractionDate()));
        return toDto(interaction);
    }

    @Transactional(readOnly = true)
    public PartyInteraction get(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id).map(InteractionService::toDto)
                .orElseThrow(() -> new NotFoundException("PartyInteraction", id));
    }

    @Transactional(readOnly = true)
    public Page<PartyInteraction> list(String tenantId, String partyId, int offset, int limit) {
        PageRequest page = PageRequest.of(offset / limit, limit, Sort.by("interactionDate").descending());
        Page<Interaction> result = partyId == null
                ? repository.findByTenantId(tenantId, page)
                : repository.findByTenantIdAndPartyId(tenantId, partyId, page);
        return result.map(InteractionService::toDto);
    }

    static PartyInteraction toDto(Interaction i) {
        return new PartyInteraction(
                i.getId(),
                BASE_PATH + "/" + i.getId(),
                i.getInteractionDate(),
                List.of(new PartyInteraction.Ref(i.getPartyId(), null, "customer")),
                List.of(new PartyInteraction.Ref(i.getChannel(), null, null)),
                i.getDecisionId(),
                new PartyInteraction.Ref(i.getOfferingId(), null, null),
                i.getCampaignId() == null ? null : new PartyInteraction.Ref(i.getCampaignId(), null, null),
                i.getOutcome(),
                PartyInteraction.TYPE,
                PartyInteraction.BASE_TYPE);
    }
}
