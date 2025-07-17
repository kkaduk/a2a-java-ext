package io.a2a.receptionist.repository.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentMatchDTO {
    private String name;
    private String url;
    private String version;
    private List<AgentSkillDTO> skills;
}
