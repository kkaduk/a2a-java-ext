package io.a2a.receptionist.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

/**
 * Request to invoke a specific skill on an agent.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillInvocationRequest {
    @JsonProperty("agentName")
    private String agentName;
    
    @JsonProperty("skillId")
    private String skillId;
    
    @JsonProperty("input")
    private String input;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("contextId")
    private String contextId;
}