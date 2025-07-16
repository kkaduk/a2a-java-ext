
package io.a2a.receptionist.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Detailed information about a specific skill capability.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillCapability {
    @JsonProperty("skillId")
    private String skillId;
    
    @JsonProperty("skillName")
    private String skillName;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("tags")
    private List<String> tags;
    
    @JsonProperty("inputModes")
    private List<String> inputModes;
    
    @JsonProperty("outputModes")
    private List<String> outputModes;
    
    @JsonProperty("examples")
    private List<String> examples;
}