package io.nbaplatform.platform.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/** TMF630 Error resource. */
public record TmfError(
        String code,
        String reason,
        String message,
        String status,
        @JsonProperty("@type") String type) {

    public static TmfError of(int status, String reason, String message) {
        return new TmfError(String.valueOf(status), reason, message, String.valueOf(status), "Error");
    }
}
