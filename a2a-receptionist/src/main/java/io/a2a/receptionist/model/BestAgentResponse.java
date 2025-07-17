package io.a2a.receptionist.model;

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
public class BestAgentResponse {
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("agent")
    private AgentSkillDocument agent;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
}