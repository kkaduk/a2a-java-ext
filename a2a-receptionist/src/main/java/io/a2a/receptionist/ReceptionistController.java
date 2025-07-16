
package io.a2a.receptionist;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import io.a2a.receptionist.model.BestAgentResponse;
import io.a2a.receptionist.model.CapabilityDiscoveryResponse;
import io.a2a.receptionist.model.CapabilityQuery;
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
    public Mono<CapabilityDiscoveryResponse> discoverCapabilities(
            @RequestBody CapabilityQuery query) {
        
        return receptionist.findAgentsByCapability(query)
            .map(agents -> CapabilityDiscoveryResponse.builder()
                .success(true)
                .agentCount(agents.size())
                .agents(agents)
                .build())
            .onErrorReturn(CapabilityDiscoveryResponse.builder()
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
    public Mono<BestAgentResponse> findBestAgent(@RequestBody CapabilityQuery query) {
        
        return receptionist.findBestAgentForCapability(query)
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
    public Mono<CapabilityDiscoveryResponse> discoverAllCapabilities() {
        
        return receptionist.discoverAllCapabilities()
            .map(agents -> CapabilityDiscoveryResponse.builder()
                .success(true)
                .agentCount(agents.size())
                .agents(agents)
                .build());
    }
}