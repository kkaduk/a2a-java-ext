package io.a2a.receptionist.model;

import java.util.List;

import io.a2a.receptionist.repository.model.dto.AgentSkillDTO;
import lombok.Data;

@Data
public class AgentSkillDocument {
    private String agentName;
    private String url;
    private Double confidence;
    private List<AgentSkillDTO> skills;
}
