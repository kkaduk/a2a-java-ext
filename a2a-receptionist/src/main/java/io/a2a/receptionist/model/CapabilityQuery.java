package io.a2a.receptionist.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Query object for finding agents by capability.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapabilityQuery {
    @JsonProperty("skillId")
    private String skillId;

    @JsonProperty("requiredTags")
    private List<String> requiredTags;

    @JsonProperty("keywords")
    private List<String> keywords;

    @JsonProperty("inputMode")
    private String inputMode;

    @JsonProperty("outputMode")
    private String outputMode;

    @JsonProperty("maxResults")
    private Integer maxResults;

    @JsonProperty("matchAllTags")
    private Boolean matchAllTags; // true = AND, false = OR
}