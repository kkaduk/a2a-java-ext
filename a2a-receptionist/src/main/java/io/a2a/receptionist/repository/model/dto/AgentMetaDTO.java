package io.a2a.receptionist.repository.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentMetaDTO {
    private String agentName;
    private List<A2AAgentSkillDTO> skills;
}