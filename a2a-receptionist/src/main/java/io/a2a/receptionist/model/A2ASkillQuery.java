package io.a2a.receptionist.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query object for finding agents by capability.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2ASkillQuery {
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