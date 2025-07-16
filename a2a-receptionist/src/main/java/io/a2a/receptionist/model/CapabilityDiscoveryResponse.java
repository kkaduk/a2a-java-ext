// src/main/java/net/kaduk/a2a/CapabilityDiscoveryResponse.java
package io.a2a.receptionist.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.a2a.spec.AgentCapabilities;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapabilityDiscoveryResponse {
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("agentCount")
    private Integer agentCount;
    
    @JsonProperty("agents")
    private List<AgentCapabilities> agents;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
}