package io.nbaplatform.campaign.api;

import io.nbaplatform.campaign.domain.Campaign;
import io.nbaplatform.campaign.domain.CampaignRepository;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.EventTypes;
import io.nbaplatform.events.Topics;
import io.nbaplatform.events.ValidFor;
import io.nbaplatform.platform.outbox.OutboxWriter;
import io.nbaplatform.platform.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {

    static final String BASE_PATH = "/tmf-api/promotionManagement/v4/promotion";
    static final String ACTION_TYPE = "recommendOffering";

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final CampaignRepository repository;
    private final OutboxWriter outbox;

    public CampaignService(CampaignRepository repository, OutboxWriter outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public Promotion create(String tenantId, Promotion request) {
        Campaign entity = new Campaign(UUID.randomUUID().toString(), tenantId);
        apply(request, entity);
        if (entity.getLifecycleStatus() == null) {
            entity.setLifecycleStatus("draft");
        }
        entity.touch();
        return publish(repository.saveAndFlush(entity), false);
    }

    @Transactional(readOnly = true)
    public Promotion get(String tenantId, String id) {
        return toDto(find(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<Promotion> list(String tenantId, String lifecycleStatus, int offset, int limit) {
        PageRequest page = PageRequest.of(offset / limit, limit, Sort.by("lastUpdate").descending());
        Page<Campaign> result = lifecycleStatus == null
                ? repository.findByTenantId(tenantId, page)
                : repository.findByTenantIdAndLifecycleStatus(tenantId, lifecycleStatus, page);
        return result.map(CampaignService::toDto);
    }

    @Transactional
    public Promotion update(String tenantId, String id, Promotion merged) {
        Campaign entity = find(tenantId, id);
        apply(merged, entity);
        entity.touch();
        return publish(repository.saveAndFlush(entity), false);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        Campaign entity = find(tenantId, id);
        repository.delete(entity);
        publish(entity, true);
    }

    private Campaign find(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new NotFoundException("Promotion", id));
    }

    private Promotion publish(Campaign c, boolean deleted) {
        Promotion dto = toDto(c);
        outbox.append(Topics.CAMPAIGN_CHANGED, EventTypes.CAMPAIGN_CHANGED, c.getTenantId(), c.getId(),
                new CampaignChanged(c.getId(), c.getName(), c.getLifecycleStatus(), dto.validFor(), c.getChannels(),
                        c.getEligibilityRule(), c.getControlGroupPercent(), c.getMaxImpressionsPerDay(),
                        c.getActions(), deleted, c.getVersion()));
        return dto;
    }

    static void validateRule(String rule) {
        if (rule == null || rule.isBlank()) {
            return;
        }
        try {
            PARSER.parseExpression(rule);
        } catch (ParseException e) {
            throw new IllegalArgumentException("eligibilityRule is not a valid expression: " + e.getMessage());
        }
    }

    private static void apply(Promotion dto, Campaign c) {
        validateRule(dto.eligibilityRule());
        c.setName(dto.name());
        c.setDescription(dto.description());
        if (dto.lifecycleStatus() != null) {
            c.setLifecycleStatus(dto.lifecycleStatus());
        }
        c.setValidFrom(dto.validFor() == null ? null : dto.validFor().startDateTime());
        c.setValidTo(dto.validFor() == null ? null : dto.validFor().endDateTime());
        c.setChannels(dto.channel() == null ? List.of()
                : dto.channel().stream().map(Promotion.Ref::id).filter(id -> id != null && !id.isBlank()).toList());
        c.setEligibilityRule(dto.eligibilityRule() == null || dto.eligibilityRule().isBlank()
                ? null : dto.eligibilityRule());
        c.setControlGroupPercent(dto.controlGroupPercent() == null ? 0.0 : dto.controlGroupPercent());
        c.setMaxImpressionsPerDay(dto.maxImpressionsPerDay() == null ? 0 : dto.maxImpressionsPerDay());
        c.setActions(dto.action().stream()
                .map(a -> new CampaignAction(a.actionObjectId(), a.priorityLever() == null ? 1.0 : a.priorityLever()))
                .toList());
    }

    static Promotion toDto(Campaign c) {
        ValidFor validFor = c.getValidFrom() == null && c.getValidTo() == null
                ? null : new ValidFor(c.getValidFrom(), c.getValidTo());
        return new Promotion(
                c.getId(),
                BASE_PATH + "/" + c.getId(),
                c.getName(),
                c.getDescription(),
                c.getLifecycleStatus(),
                String.valueOf(c.getVersion()),
                c.getLastUpdate(),
                validFor,
                c.getChannels().stream().map(ch -> new Promotion.Ref(ch, null)).toList(),
                c.getEligibilityRule(),
                c.getControlGroupPercent(),
                c.getMaxImpressionsPerDay(),
                c.getActions().stream()
                        .map(a -> new Promotion.PromotionAction(ACTION_TYPE, a.offeringId(), a.priorityLever()))
                        .toList(),
                Promotion.TYPE,
                Promotion.BASE_TYPE);
    }
}
