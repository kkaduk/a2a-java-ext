// src/main/java/net/kaduk/a2a/CapabilityDiscoveryResponse.java
package io.a2a.receptionist.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillDiscoveryResponse {
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("agentCount")
    private Integer agentCount;
    
    @JsonProperty("agents")
    private List<AgentSkillDocument> agents;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
}