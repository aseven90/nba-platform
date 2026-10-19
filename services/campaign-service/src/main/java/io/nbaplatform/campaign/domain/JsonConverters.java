package io.nbaplatform.campaign.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.events.CampaignAction;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;

/** Stores small value objects as JSON text so the schema stays portable across PostgreSQL and test databases. */
final class JsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConverters() {
    }

    static String write(Object value) {
        try {
            return value == null ? null : MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static <T> T read(String json, TypeReference<T> type) {
        try {
            return json == null ? null : MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Converter
    static class StringList implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            return write(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            List<String> value = read(dbData, new TypeReference<>() { });
            return value == null ? List.of() : value;
        }
    }

    @Converter
    static class Actions implements AttributeConverter<List<CampaignAction>, String> {
        @Override
        public String convertToDatabaseColumn(List<CampaignAction> attribute) {
            return write(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<CampaignAction> convertToEntityAttribute(String dbData) {
            List<CampaignAction> value = read(dbData, new TypeReference<>() { });
            return value == null ? List.of() : value;
        }
    }
}
