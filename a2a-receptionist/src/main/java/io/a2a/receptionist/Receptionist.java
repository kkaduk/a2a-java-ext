package io.a2a.receptionist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.a2a.receptionist.model.A2ASkillQuery;
import io.a2a.receptionist.model.AgentSkillDocument;
import io.a2a.receptionist.model.SkillCapability;
import io.a2a.receptionist.model.SkillInvocationRequest;
import io.a2a.receptionist.model.SkillInvocationResponse;
import io.a2a.receptionist.repository.AgentRepositoryImpl;
import io.a2a.receptionist.repository.model.dto.AgentSkillDTO;
import io.a2a.receptionist.repository.model.entity.AgentEntity;
import io.a2a.receptionist.service.A2AWebClientService;
import io.a2a.spec.EventKind;
import io.a2a.spec.Message;
import io.a2a.spec.Message.Role;
import io.a2a.spec.MessageSendConfiguration;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.TextPart;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class Receptionist {

    private final AgentRepositoryImpl agentRepository;
    private final A2AWebClientService webClientService;
    private final ObjectMapper objectMapper;

    public Receptionist(AgentRepositoryImpl agentRepository,
            A2AWebClientService webClientService,
            ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.webClientService = webClientService;
        this.objectMapper = objectMapper;
    }

    public Mono<List<AgentSkillDocument>> findAgentsBySkills(A2ASkillQuery capabilityQuery) {
        return Mono.fromSupplier(() -> {
            List<AgentSkillDocument> matchingAgents = new ArrayList<>();
            List<AgentEntity> entities = agentRepository.searchByCapability(capabilityQuery);

            for (AgentEntity entity : entities) {
                try {
                    AgentSkillDocument doc = objectMapper.readValue(entity.getSkill(), AgentSkillDocument.class);
                    List<SkillCapability> matchingSkills = doc.getSkills().stream()
                            .filter(skill -> isSkillMatching(skill, capabilityQuery))
                            .map(this::convertToSkillCapability)
                            .collect(Collectors.toList());

                    if (!matchingSkills.isEmpty()) {
                        double confidence = calculateConfidence(matchingSkills, capabilityQuery);
                        if(confidence < 0.5) {
                            log.warn("Low confidence for agent {}: {}", entity.getName(), confidence);
                        }else {
                            // doc.setSkills(matchingSkills);
                            matchingAgents.add(doc);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Skill JSON parsing failed for {}: {}", entity.getName(), e.getMessage());
                }
            }

            // Remove sorting by confidence since AgentCapabilities does not have getConfidence()
            // If you want to sort by another property, adjust here.
            return matchingAgents;
        });
    }

    public Mono<Optional<AgentSkillDocument>> findBestAgentForSkill(A2ASkillQuery capabilityQuery) {
        return findAgentsBySkills(capabilityQuery)
                .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    public Mono<SkillInvocationResponse> invokeAgentSkill(SkillInvocationRequest request) {
        log.info(String.format("(KK) Invoking skill '%s' on agent '%s'", request.getSkillId(), request.getAgentName()));
        Optional<AgentEntity> agentOpt = agentRepository.findByName(request.getAgentName());

        if (agentOpt.isEmpty()) {
            return Mono.just(SkillInvocationResponse.builder()
                    .success(false)
                    .errorMessage("Agent not found: " + request.getAgentName())
                    .build());
        }

        AgentEntity agent = agentOpt.get();
        SendMessageRequest messageRequest = createMessageRequest(request, agent.getUrl());
        log.info(String.format("(KK) Sending message to agent %s at %s", agent.getName(), agent.getUrl()));
        return webClientService.sendMessage(agent.getUrl(), messageRequest)
                .map(response -> {
                    EventKind eventKind = response.getResult();
                    Message messageResult = null;
                    if (eventKind instanceof Message) {
                        messageResult = (Message) eventKind;
                    }
                    return SkillInvocationResponse.builder()
                            .success(true)
                            .result(messageResult) // Now pass actual Message result
                            .taskId(messageResult != null ? messageResult.getTaskId() : null)
                            .build();
                })
                .onErrorReturn(SkillInvocationResponse.builder()
                        .success(false)
                        .errorMessage("Skill invocation failed")
                        .build());
    }

    public Mono<List<AgentSkillDocument>> discoverAllSkills() {
        return Mono.fromSupplier(() -> {
            A2ASkillQuery emptyQuery = new A2ASkillQuery();
            List<AgentEntity> entities = agentRepository.searchByCapability(emptyQuery);

            return entities.stream().map(entity -> {
                try {
                    AgentSkillDocument doc = objectMapper.readValue(entity.getSkill(), AgentSkillDocument.class);
                    return doc;
                } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
                    log.error("Failed to parse skills for {}: {}", entity.getName(), e.getMessage());
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        });
    }

    // --- Private Utility Methods ---
    private boolean isSkillMatching(AgentSkillDTO skill, A2ASkillQuery query) {
        if (query.getSkillId() != null && skill.getId().equals(query.getSkillId()))
            return true;

        if (query.getRequiredTags() != null && !query.getRequiredTags().isEmpty()) {
            Set<String> tags = skill.getTags() != null ? new HashSet<>(skill.getTags()) : Set.of();
            if (query.getRequiredTags().stream().anyMatch(tags::contains))
                return true;
        }

        if (query.getKeywords() != null && !query.getKeywords().isEmpty()) {
            String search = (skill.getName() + " " + skill.getDescription()).toLowerCase();
            return query.getKeywords().stream().anyMatch(k -> search.contains(k.toLowerCase()));
        }

        return false;
    }

    private SkillCapability convertToSkillCapability(AgentSkillDTO skill) {
        return SkillCapability.builder()
                .skillId(skill.getId())
                .skillName(skill.getName())
                .description(skill.getDescription())
                .tags(skill.getTags())
                .inputModes(List.of("text/plain"))
                .outputModes(List.of("text/plain"))
                .build();
    }

    private double calculateConfidence(List<SkillCapability> skills, A2ASkillQuery query) {
        if (skills.isEmpty())
            return 0.0;
        double score = 0.5 + skills.size() * 0.1;

        if (query.getSkillId() != null &&
                skills.stream().anyMatch(s -> s.getSkillId().equals(query.getSkillId())))
            score += 0.3;

        return Math.min(score, 1.0);
    }

    private SendMessageRequest createMessageRequest(SkillInvocationRequest request, String agentUrl) {
        Message msg = new Message.Builder()
                .role(Role.USER)
                .parts(new TextPart(Optional.ofNullable(request.getInput()).orElse("")))
                .messageId(UUID.randomUUID().toString())
                .contextId(request.getSkillId())
                .taskId(UUID.randomUUID().toString())
                .build();

        return new SendMessageRequest(
                UUID.randomUUID().toString(),
                new MessageSendParams.Builder()
                        .message(msg)
                        .configuration(new MessageSendConfiguration.Builder()
                                .acceptedOutputModes(List.of("text/plain"))
                                .blocking(true)
                                .build())
                        .build()
        );
    }
}
