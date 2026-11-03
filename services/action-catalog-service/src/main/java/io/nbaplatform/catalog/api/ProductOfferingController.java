package io.nbaplatform.catalog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.platform.tenant.TenantContext;
import io.nbaplatform.platform.web.JsonMergePatch;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** TMF620 Product Catalog Management: ProductOffering resource. */
@RestController
@Validated
@RequestMapping(OfferingService.BASE_PATH)
public class ProductOfferingController {

    private final OfferingService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ProductOfferingController(OfferingService service, ObjectMapper objectMapper, Validator validator) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<ProductOffering> create(@Valid @RequestBody ProductOffering request) {
        ProductOffering created = service.create(TenantContext.require(), request);
        return ResponseEntity.created(URI.create(created.href())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ProductOffering>> list(
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        Page<ProductOffering> page = service.list(TenantContext.require(), lifecycleStatus, offset, limit);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Result-Count", String.valueOf(page.getNumberOfElements()))
                .body(page.getContent());
    }

    @GetMapping("/{id}")
    public ProductOffering get(@PathVariable String id) {
        return service.get(TenantContext.require(), id);
    }

    /** JSON Merge Patch (RFC 7386), the mandatory PATCH format in TMF630. */
    @PatchMapping(value = "/{id}", consumes = {"application/merge-patch+json", "application/json"})
    public ProductOffering patch(@PathVariable String id, @RequestBody JsonNode patch) {
        String tenant = TenantContext.require();
        ProductOffering current = service.get(tenant, id);
        ProductOffering merged = JsonMergePatch.apply(objectMapper, validator, current, patch, ProductOffering.class);
        return service.update(tenant, id, merged);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(TenantContext.require(), id);
        return ResponseEntity.noContent().build();
    }
}
