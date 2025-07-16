package io.a2a.receptionist.model;

import java.util.List;

import lombok.Data;

@Data
public class AgentSkillDocument {
    private String agentName;
    private List<AgentSkillDTO> skills;
}
