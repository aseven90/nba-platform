package io.nbaplatform.catalog.api;

import io.nbaplatform.catalog.domain.ActionOffering;
import io.nbaplatform.catalog.domain.ActionOfferingRepository;
import io.nbaplatform.events.EventTypes;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.Topics;
import io.nbaplatform.events.ValidFor;
import io.nbaplatform.platform.outbox.OutboxWriter;
import io.nbaplatform.platform.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferingService {

    static final String BASE_PATH = "/tmf-api/productCatalogManagement/v4/productOffering";

    private final ActionOfferingRepository repository;
    private final OutboxWriter outbox;

    public OfferingService(ActionOfferingRepository repository, OutboxWriter outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public ProductOffering create(String tenantId, ProductOffering request) {
        ActionOffering entity = new ActionOffering(UUID.randomUUID().toString(), tenantId);
        apply(request, entity);
        if (entity.getLifecycleStatus() == null) {
            entity.setLifecycleStatus("In design");
        }
        entity.touch();
        return publish(repository.saveAndFlush(entity), false);
    }

    @Transactional(readOnly = true)
    public ProductOffering get(String tenantId, String id) {
        return toDto(find(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<ProductOffering> list(String tenantId, String lifecycleStatus, int offset, int limit) {
        PageRequest page = PageRequest.of(offset / limit, limit, Sort.by("lastUpdate").descending());
        Page<ActionOffering> result = lifecycleStatus == null
                ? repository.findByTenantId(tenantId, page)
                : repository.findByTenantIdAndLifecycleStatus(tenantId, lifecycleStatus, page);
        return result.map(OfferingService::toDto);
    }

    /** Applies a merged representation (JSON Merge Patch already applied by the caller). */
    @Transactional
    public ProductOffering update(String tenantId, String id, ProductOffering merged) {
        ActionOffering entity = find(tenantId, id);
        apply(merged, entity);
        entity.touch();
        return publish(repository.saveAndFlush(entity), false);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        ActionOffering entity = find(tenantId, id);
        repository.delete(entity);
        publish(entity, true);
    }

    private ActionOffering find(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("ProductOffering", id));
    }

    private ProductOffering publish(ActionOffering e, boolean deleted) {
        ProductOffering dto = toDto(e);
        outbox.append(Topics.CATALOG_OFFERING_CHANGED, EventTypes.OFFERING_CHANGED, e.getTenantId(), e.getId(),
                new OfferingChanged(e.getId(), e.getName(), e.getDescription(), e.getCategory(),
                        e.getLifecycleStatus(), e.getChannels(), e.getBusinessValue(), dto.validFor(),
                        e.getPropensityModel(), deleted, e.getVersion()));
        return dto;
    }

    private static void apply(ProductOffering dto, ActionOffering e) {
        e.setName(dto.name());
        e.setDescription(dto.description());
        if (dto.lifecycleStatus() != null) {
            e.setLifecycleStatus(dto.lifecycleStatus());
        }
        e.setValidFrom(dto.validFor() == null ? null : dto.validFor().startDateTime());
        e.setValidTo(dto.validFor() == null ? null : dto.validFor().endDateTime());
        e.setCategory(dto.category() == null || dto.category().isEmpty() ? null : dto.category().get(0).name());
        e.setChannels(dto.channel() == null ? List.of()
                : dto.channel().stream().map(ProductOffering.Ref::id).filter(c -> c != null && !c.isBlank()).toList());
        e.setBusinessValue(dto.businessValue() == null ? 0.0 : dto.businessValue());
        e.setPropensityModel(dto.propensityModel());
    }

    static ProductOffering toDto(ActionOffering e) {
        ValidFor validFor = e.getValidFrom() == null && e.getValidTo() == null
                ? null : new ValidFor(e.getValidFrom(), e.getValidTo());
        return new ProductOffering(
                e.getId(),
                BASE_PATH + "/" + e.getId(),
                e.getName(),
                e.getDescription(),
                e.getLifecycleStatus(),
                String.valueOf(e.getVersion()),
                e.getLastUpdate(),
                validFor,
                e.getCategory() == null ? null : List.of(new ProductOffering.Ref(null, e.getCategory())),
                e.getChannels().stream().map(c -> new ProductOffering.Ref(c, null)).toList(),
                e.getBusinessValue(),
                e.getPropensityModel(),
                ProductOffering.TYPE,
                ProductOffering.BASE_TYPE);
    }
}
