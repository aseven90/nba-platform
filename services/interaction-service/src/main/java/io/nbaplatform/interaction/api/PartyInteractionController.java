package io.nbaplatform.interaction.api;

import io.nbaplatform.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TMF683 Party Interaction Management. POST honours an optional {@code Idempotency-Key} header: a retried
 * request with the same key returns the original interaction with 200 instead of recording a duplicate.
 */
@RestController
@Validated
@RequestMapping(InteractionService.BASE_PATH)
public class PartyInteractionController {

    private final InteractionService service;

    public PartyInteractionController(InteractionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PartyInteraction> create(
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody PartyInteraction request) {
        String tenant = TenantContext.require();
        Optional<PartyInteraction> existing = service.findByIdempotencyKey(tenant, idempotencyKey);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }
        try {
            PartyInteraction created = service.record(tenant, idempotencyKey, request);
            return ResponseEntity.created(URI.create(created.href())).body(created);
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key won; return its result.
            return service.findByIdempotencyKey(tenant, idempotencyKey).map(ResponseEntity::ok).orElseThrow(() -> race);
        }
    }

    @GetMapping("/{id}")
    public PartyInteraction get(@PathVariable String id) {
        return service.get(TenantContext.require(), id);
    }

    @GetMapping
    public ResponseEntity<List<PartyInteraction>> list(
            @RequestParam(name = "relatedParty.id", required = false) String partyId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        Page<PartyInteraction> page = service.list(TenantContext.require(), partyId, offset, limit);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Result-Count", String.valueOf(page.getNumberOfElements()))
                .body(page.getContent());
    }
}
