package io.a2a.receptionist.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.a2a.spec.AgentCapabilities;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BestAgentResponse {
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("agent")
    private AgentCapabilities agent;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
}