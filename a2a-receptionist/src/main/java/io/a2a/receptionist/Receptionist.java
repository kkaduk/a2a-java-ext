package io.a2a.receptionist;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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
import io.a2a.spec.Part;
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

    // Common business/technical terms for semantic matching
    private static final Map<String, Set<String>> SEMANTIC_GROUPS = Map.of(
        "review", Set.of("review", "assessment", "evaluation", "analysis", "audit", "examination", "inspection"),
        "executive", Set.of("executive", "leadership", "management", "strategic", "senior", "c-level", "director"),
        "banking", Set.of("banking", "financial", "finance", "fintech", "monetary", "credit", "lending"),
        "ai", Set.of("ai", "artificial intelligence", "machine learning", "ml", "intelligent", "smart", "automated"),
        "product", Set.of("product", "service", "offering", "solution", "platform", "system"),
        "digital", Set.of("digital", "online", "electronic", "cyber", "virtual", "tech", "technology")
    );

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
            
            // Get all agents if we need loose matching, otherwise use existing DB filtering for exact matches
            List<AgentEntity> entities;
            if (hasExactCriteria(capabilityQuery)) {
                entities = agentRepository.searchByCapability(capabilityQuery);
            } else {
                // For loose matching, get all agents and do in-memory filtering
                entities = agentRepository.searchByCapability(new A2ASkillQuery());
            }

            log.info("Evaluating {} agents for query: {}", entities.size(), capabilityQuery);

            for (AgentEntity entity : entities) {
                try {
                    AgentSkillDocument doc = objectMapper.readValue(entity.getSkill(), AgentSkillDocument.class);
                    doc.setAgentName(entity.getName());
                    doc.setUrl(entity.getUrl());

                    // Enhanced skill matching with confidence scoring
                    List<SkillMatchResult> matchResults = doc.getSkills().stream()
                            .map(skill -> evaluateSkillMatch(skill, capabilityQuery))
                            .filter(result -> result.confidence > 0.1) // Filter out very low confidence matches
                            .collect(Collectors.toList());

                    if (!matchResults.isEmpty()) {
                        // Calculate overall agent confidence based on best matching skill
                        double maxConfidence = matchResults.stream()
                                .mapToDouble(result -> result.confidence)
                                .max()
                                .orElse(0.0);

                        // Also consider average confidence for multiple matching skills
                        double avgConfidence = matchResults.stream()
                                .mapToDouble(result -> result.confidence)
                                .average()
                                .orElse(0.0);

                        // Combine max and average with slight weight towards the best match
                        double finalConfidence = (maxConfidence * 0.7) + (avgConfidence * 0.3);
                        
                        // Boost confidence if multiple skills match
                        if (matchResults.size() > 1) {
                            finalConfidence = Math.min(1.0, finalConfidence * 1.1);
                        }

                        doc.setConfidence(finalConfidence);
                        matchingAgents.add(doc);
                        
                        log.debug("Agent {} matched with confidence {}: {} skills matched", 
                                entity.getName(), finalConfidence, matchResults.size());
                    }
                } catch (Exception e) {
                    log.warn("Skill JSON parsing failed for {}: {}", entity.getName(), e.getMessage());
                }
            }

            // Sort by confidence (highest first) and apply result limits
            matchingAgents.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
            
            int maxResults = Optional.ofNullable(capabilityQuery.getMaxResults()).orElse(10);
            if (matchingAgents.size() > maxResults) {
                matchingAgents = matchingAgents.subList(0, maxResults);
            }

            log.info("Found {} agents matching query with confidence >= 0.1", matchingAgents.size());
            if (!matchingAgents.isEmpty()) {
                log.info("Top match: {} with confidence {}", 
                        matchingAgents.get(0).getAgentName(), 
                        matchingAgents.get(0).getConfidence());
            }

            return matchingAgents;
        });
    }

    /**
     * Enhanced skill matching that considers semantic similarity and loose text matching
     */
    private SkillMatchResult evaluateSkillMatch(AgentSkillDTO skill, A2ASkillQuery query) {
        double confidence = 0.0;
        List<String> matchReasons = new ArrayList<>();

        // 1. Exact skill ID match (highest confidence)
        if (query.getSkillId() != null && skill.getId().equalsIgnoreCase(query.getSkillId())) {
            confidence = 1.0;
            matchReasons.add("exact-id-match");
            return new SkillMatchResult(skill, confidence, matchReasons);
        }

        // 2. Tag matching with semantic expansion
        if (query.getRequiredTags() != null && !query.getRequiredTags().isEmpty()) {
            Set<String> skillTags = skill.getTags() != null ? 
                    new HashSet<>(skill.getTags().stream().map(String::toLowerCase).toList()) : 
                    new HashSet<>();

            double tagMatchScore = calculateTagMatchScore(skillTags, query.getRequiredTags(), query.getMatchAllTags());
            if (tagMatchScore > 0) {
                confidence = Math.max(confidence, tagMatchScore);
                matchReasons.add("tag-match-" + String.format("%.2f", tagMatchScore));
            }
        }

        // 3. Keyword matching in skill text with semantic similarity
        if (query.getKeywords() != null && !query.getKeywords().isEmpty()) {
            double keywordScore = calculateKeywordMatchScore(skill, query.getKeywords());
            if (keywordScore > 0) {
                confidence = Math.max(confidence, keywordScore);
                matchReasons.add("keyword-match-" + String.format("%.2f", keywordScore));
            }
        }

        // 4. Semantic description matching (new feature)
        if (hasDescriptiveText(query)) {
            double semanticScore = calculateSemanticMatchScore(skill, query);
            if (semanticScore > 0) {
                confidence = Math.max(confidence, semanticScore);
                matchReasons.add("semantic-match-" + String.format("%.2f", semanticScore));
            }
        }

        // 5. Fallback: loose text similarity for any query with text content
        if (confidence == 0.0 && hasAnyTextCriteria(query)) {
            double fallbackScore = calculateFallbackTextSimilarity(skill, query);
            if (fallbackScore > 0.2) { // Only consider reasonable fallback matches
                confidence = fallbackScore;
                matchReasons.add("text-similarity-" + String.format("%.2f", fallbackScore));
            }
        }

        return new SkillMatchResult(skill, confidence, matchReasons);
    }

    private double calculateTagMatchScore(Set<String> skillTags, List<String> requiredTags, Boolean matchAllTags) {
        if (skillTags.isEmpty()) return 0.0;

        boolean matchAll = Boolean.TRUE.equals(matchAllTags);
        Set<String> queryTags = requiredTags.stream().map(String::toLowerCase).collect(Collectors.toSet());
        
        // Direct tag matches
        Set<String> directMatches = new HashSet<>(skillTags);
        directMatches.retainAll(queryTags);
        
        // Semantic tag matches
        Set<String> semanticMatches = findSemanticTagMatches(skillTags, queryTags);
        
        int totalMatches = directMatches.size() + semanticMatches.size();
        
        if (matchAll) {
            // All tags must match (directly or semantically)
            return totalMatches >= queryTags.size() ? 0.9 : (double) totalMatches / queryTags.size() * 0.7;
        } else {
            // Any tag matches
            if (totalMatches > 0) {
                double baseScore = Math.min(0.8, (double) totalMatches / queryTags.size());
                return directMatches.isEmpty() ? baseScore * 0.8 : baseScore; // Slight penalty for semantic-only matches
            }
        }
        
        return 0.0;
    }

    private Set<String> findSemanticTagMatches(Set<String> skillTags, Set<String> queryTags) {
        Set<String> matches = new HashSet<>();
        
        for (String skillTag : skillTags) {
            for (String queryTag : queryTags) {
                if (areSemanticallySimilar(skillTag, queryTag)) {
                    matches.add(queryTag);
                }
            }
        }
        
        return matches;
    }

    private double calculateKeywordMatchScore(AgentSkillDTO skill, List<String> keywords) {
        String searchText = buildSearchableText(skill);
        String[] words = searchText.split("\\s+");
        Set<String> skillWords = Arrays.stream(words)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        int exactMatches = 0;
        int semanticMatches = 0;
        
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            
            // Check for exact word matches
            if (skillWords.contains(lowerKeyword) || searchText.contains(lowerKeyword)) {
                exactMatches++;
                continue;
            }
            
            // Check for semantic matches
            boolean foundSemantic = skillWords.stream()
                    .anyMatch(word -> areSemanticallySimilar(word, lowerKeyword));
                    
            if (foundSemantic) {
                semanticMatches++;
            }
        }
        
        if (exactMatches + semanticMatches == 0) return 0.0;
        
        double exactScore = (double) exactMatches / keywords.size() * 0.8;
        double semanticScore = (double) semanticMatches / keywords.size() * 0.6;
        
        return Math.min(0.85, exactScore + semanticScore);
    }

    private double calculateSemanticMatchScore(AgentSkillDTO skill, A2ASkillQuery query) {
        String skillText = buildSearchableText(skill);
        List<String> allQueryTerms = extractAllQueryTerms(query);
        
        if (allQueryTerms.isEmpty()) return 0.0;
        
        int semanticMatches = 0;
        for (String term : allQueryTerms) {
            if (containsSemanticMatch(skillText, term)) {
                semanticMatches++;
            }
        }
        
        double semanticRatio = (double) semanticMatches / allQueryTerms.size();
        return semanticRatio > 0.3 ? Math.min(0.75, semanticRatio) : 0.0; // Threshold for semantic matching
    }

    private double calculateFallbackTextSimilarity(AgentSkillDTO skill, A2ASkillQuery query) {
        String skillText = buildSearchableText(skill).toLowerCase();
        List<String> allQueryTerms = extractAllQueryTerms(query);
        
        if (allQueryTerms.isEmpty()) return 0.0;
        
        // Calculate Jaccard similarity based on word overlap
        Set<String> skillWords = new HashSet<>(Arrays.asList(skillText.split("\\s+")));
        Set<String> queryWords = allQueryTerms.stream()
                .flatMap(term -> Arrays.stream(term.toLowerCase().split("\\s+")))
                .collect(Collectors.toSet());
        
        Set<String> intersection = new HashSet<>(skillWords);
        intersection.retainAll(queryWords);
        
        Set<String> union = new HashSet<>(skillWords);
        union.addAll(queryWords);
        
        double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        
        // Additional scoring for partial word matches
        double partialScore = 0.0;
        for (String queryWord : queryWords) {
            for (String skillWord : skillWords) {
                if (skillWord.contains(queryWord) || queryWord.contains(skillWord)) {
                    partialScore += 0.1;
                }
            }
        }
        
        return Math.min(0.6, jaccard + Math.min(0.3, partialScore)); // Cap fallback scores
    }

    // --- Utility Methods ---

    private boolean hasExactCriteria(A2ASkillQuery query) {
        return query.getSkillId() != null && !query.getSkillId().trim().isEmpty();
    }

    private boolean hasDescriptiveText(A2ASkillQuery query) {
        return (query.getKeywords() != null && query.getKeywords().stream().anyMatch(k -> k.length() > 3)) ||
               (query.getRequiredTags() != null && query.getRequiredTags().stream().anyMatch(t -> t.length() > 3));
    }

    private boolean hasAnyTextCriteria(A2ASkillQuery query) {
        return (query.getKeywords() != null && !query.getKeywords().isEmpty()) ||
               (query.getRequiredTags() != null && !query.getRequiredTags().isEmpty()) ||
               (query.getSkillId() != null && !query.getSkillId().trim().isEmpty());
    }

    private String buildSearchableText(AgentSkillDTO skill) {
        StringBuilder sb = new StringBuilder();
        if (skill.getName() != null) sb.append(skill.getName()).append(" ");
        if (skill.getDescription() != null) sb.append(skill.getDescription()).append(" ");
        if (skill.getTags() != null) {
            skill.getTags().forEach(tag -> sb.append(tag).append(" "));
        }
        return sb.toString().trim();
    }

    private List<String> extractAllQueryTerms(A2ASkillQuery query) {
        List<String> terms = new ArrayList<>();
        
        if (query.getSkillId() != null) terms.add(query.getSkillId());
        if (query.getKeywords() != null) terms.addAll(query.getKeywords());
        if (query.getRequiredTags() != null) terms.addAll(query.getRequiredTags());
        
        return terms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private boolean areSemanticallySimilar(String word1, String word2) {
        word1 = word1.toLowerCase().trim();
        word2 = word2.toLowerCase().trim();
        
        if (word1.equals(word2)) return true;
        
        // Check semantic groups
        for (Set<String> group : SEMANTIC_GROUPS.values()) {
            if (group.contains(word1) && group.contains(word2)) {
                return true;
            }
        }
        
        // Check for common prefixes/suffixes (e.g., "analyze" vs "analysis")
        return (word1.length() > 4 && word2.length() > 4) &&
               (word1.startsWith(word2.substring(0, Math.min(4, word2.length()))) ||
                word2.startsWith(word1.substring(0, Math.min(4, word1.length()))));
    }

    private boolean containsSemanticMatch(String text, String term) {
        String lowerText = text.toLowerCase();
        String lowerTerm = term.toLowerCase();
        
        if (lowerText.contains(lowerTerm)) return true;
        
        // Check if any word in the text is semantically similar to the term
        String[] words = lowerText.split("\\s+");
        return Arrays.stream(words).anyMatch(word -> areSemanticallySimilar(word, lowerTerm));
    }

    // Helper class to store match results
    private static class SkillMatchResult {
        final AgentSkillDTO skill;
        final double confidence;
        final List<String> matchReasons;
        
        SkillMatchResult(AgentSkillDTO skill, double confidence, List<String> matchReasons) {
            this.skill = skill;
            this.confidence = confidence;
            this.matchReasons = matchReasons;
        }
    }

    // Keep existing methods unchanged
    public Mono<Optional<AgentSkillDocument>> findBestAgentForSkill(A2ASkillQuery capabilityQuery) {
        return findAgentsBySkills(capabilityQuery)
                .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    public Mono<SkillInvocationResponse> invokeAgentSkill(SkillInvocationRequest request) {
        log.info(String.format("Invoking skill '%s' on agent '%s'", request.getSkillId(), request.getAgentName()));
        Optional<AgentEntity> agentOpt = agentRepository.findByName(request.getAgentName());

        if (agentOpt.isEmpty()) {
            return Mono.just(SkillInvocationResponse.builder()
                    .success(false)
                    .errorMessage("Agent not found: " + request.getAgentName())
                    .build());
        }

        AgentEntity agent = agentOpt.get();
        SendMessageRequest messageRequest = createMessageRequest(request, agent.getUrl());
        log.info(String.format("Sending message to agent %s at %s", agent.getName(), agent.getUrl()));
        return webClientService.sendMessage(agent.getUrl(), messageRequest)
                .map(response -> {
                    EventKind eventKind = response.getResult();
                    Message messageResult = null;
                    if (eventKind instanceof Message) {
                        messageResult = (Message) eventKind;
                    }
                    return SkillInvocationResponse.builder()
                            .success(true)
                            .result(messageResult)
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
                    doc.setAgentName(entity.getName());
                    doc.setUrl(entity.getUrl());
                    return doc;
                } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
                    log.error("Failed to parse skills for {}: {}", entity.getName(), e.getMessage());
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        });
    }

    // Keep existing utility methods
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

    private SendMessageRequest createMessageRequest(SkillInvocationRequest request, String agentUrl) {
        List<Part<?>> parts = Optional.ofNullable(request.getInput())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(TextPart::new)
                .map(p -> (Part<?>) p)
                .collect(Collectors.toList());

        if (parts.isEmpty()) {
            parts = List.of(new TextPart(""));
        }

        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        if (request.getSkillId() != null) {
            metadata.put("skillId", request.getSkillId());
        }
        if (request.getAgentName() != null) {
            metadata.put("agentName", request.getAgentName());
        }
        if (agentUrl != null) {
            metadata.put("agentUrl", agentUrl);
        }

        Message msg = new Message.Builder()
                .role(Message.Role.USER)
                .parts(parts)
                .messageId(UUID.randomUUID().toString())
                .contextId(Optional.ofNullable(request.getContextId()).orElse(request.getSkillId()))
                .taskId(UUID.randomUUID().toString())
                .metadata(metadata)
                .build();

        return new SendMessageRequest(
                UUID.randomUUID().toString(),
                new MessageSendParams.Builder()
                        .message(msg)
                        .configuration(new MessageSendConfiguration.Builder()
                                .acceptedOutputModes(List.of("text/plain"))
                                .blocking(true)
                                .build())
                        .build());
    }
}