package io.nbaplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.platform.outbox.OutboxMessage;
import io.nbaplatform.platform.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductOfferingApiTest {

    private static final String BASE = "/tmf-api/productCatalogManagement/v4/productOffering";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    OutboxRepository outbox;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
    }

    @Test
    void createReadPatchDeleteEmitsEvents() throws Exception {
        String body = """
                {"name":"Premium upgrade","lifecycleStatus":"Active","businessValue":120.0,
                 "channel":[{"id":"web"},{"id":"app"}],"category":[{"name":"upsell"}],
                 "propensityModel":{"version":"v1","intercept":-2.0,"coefficients":{"tenureMonths":0.02}}}
                """;
        String created = mvc.perform(post(BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$['@type']").value("NbaActionOffering"))
                .andExpect(jsonPath("$['@baseType']").value("ProductOffering"))
                .andExpect(jsonPath("$.channel", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").asText();

        mvc.perform(get(BASE + "/" + id).header("X-Tenant-Id", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Premium upgrade"));

        mvc.perform(patch(BASE + "/" + id).header("X-Tenant-Id", "acme")
                        .contentType("application/merge-patch+json")
                        .content("{\"businessValue\":150.0,\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessValue").value(150.0))
                .andExpect(jsonPath("$.name").value("Premium upgrade"));

        mvc.perform(get(BASE).header("X-Tenant-Id", "acme").param("lifecycleStatus", "Active"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));

        mvc.perform(delete(BASE + "/" + id).header("X-Tenant-Id", "acme"))
                .andExpect(status().isNoContent());

        var events = outbox.findAll();
        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(e -> assertThat(e.getMessageKey()).isEqualTo("acme:" + id));
        JsonNode last = mapper.readTree(events.get(2).getPayload());
        assertThat(last.get("type").asText()).isEqualTo("io.nbaplatform.catalog.offering.changed.v1");
        assertThat(last.get("tenantid").asText()).isEqualTo("acme");
        assertThat(last.at("/data/deleted").asBoolean()).isTrue();
        assertThat(events).extracting(OutboxMessage::getPublishedAt).containsOnlyNulls();
    }

    @Test
    void tenantsAreIsolated() throws Exception {
        String created = mvc.perform(post(BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Offer\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").asText();

        mvc.perform(get(BASE + "/" + id).header("X-Tenant-Id", "globex"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$['@type']").value("Error"));
    }

    @Test
    void rejectsMissingTenantAndInvalidBody() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("missingTenant"));
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"businessValue\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
