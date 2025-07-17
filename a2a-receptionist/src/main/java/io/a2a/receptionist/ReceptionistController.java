
package io.a2a.receptionist;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.a2a.receptionist.model.A2ASkillQuery;
import io.a2a.receptionist.model.BestAgentResponse;
import io.a2a.receptionist.model.SkillDiscoveryResponse;
import io.a2a.receptionist.model.SkillInvocationRequest;
import io.a2a.receptionist.model.SkillInvocationResponse;
import reactor.core.publisher.Mono;


/**
 * REST controller providing A2A-compliant endpoints for capability discovery.
 */
@RestController
@RequestMapping("/a2a/receptionist")
public class ReceptionistController {

    private final Receptionist receptionist;

    public ReceptionistController(Receptionist receptionist) {
        this.receptionist = receptionist;
    }

    /**
     * Discover agents by capability query.
     */
    @PostMapping(value = "/discover", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SkillDiscoveryResponse> discoverCapabilities(
            @RequestBody A2ASkillQuery query) {
        
        return receptionist.findAgentsBySkills(query)
            .map(agents -> SkillDiscoveryResponse.builder()
                .success(true)
                .agentCount(agents.size())
                .agents(agents)
                .build())
            .onErrorReturn(SkillDiscoveryResponse.builder()
                .success(false)
                .errorMessage("Failed to discover capabilities")
                .build());
    }

    /**
     * Find the best agent for a specific capability.
     */
    @PostMapping(value = "/find-best", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<BestAgentResponse> findBestAgent(@RequestBody A2ASkillQuery query) {
        
        return receptionist.findBestAgentForSkill(query)
            .map(agentOpt -> {
                if (agentOpt.isPresent()) {
                    return BestAgentResponse.builder()
                        .success(true)
                        .agent(agentOpt.get())
                        .build();
                } else {
                    return BestAgentResponse.builder()
                        .success(false)
                        .errorMessage("No matching agent found")
                        .build();
                }
            });
    }

    /**
     * Invoke a skill on a discovered agent.
     */
    @PostMapping(value = "/invoke", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SkillInvocationResponse> invokeSkill(
            @RequestBody SkillInvocationRequest request) {
        
        return receptionist.invokeAgentSkill(request);
    }

    /**
     * Discover all available capabilities.
     */
    @GetMapping(value = "/capabilities", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SkillDiscoveryResponse> discoverAllCapabilities() {
        
        return receptionist.discoverAllSkills()
            .map(agents -> SkillDiscoveryResponse.builder()
                .success(true)
                .agentCount(agents.size())
                .agents(agents)
                .build());
    }
}