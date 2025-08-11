package io.a2a.receptionist.service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.a2a.receptionist.model.A2AAgent;
import io.a2a.receptionist.model.A2AAgentSkill;
import io.a2a.receptionist.repository.AgentRepository;
import io.a2a.receptionist.repository.model.dto.A2AAgentSkillDTO;
import io.a2a.receptionist.repository.model.dto.AgentMetaDTO;
import io.a2a.receptionist.repository.model.entity.AgentEntity;
import io.a2a.util.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class AgentRegistry implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final AgentRepository agentRepository;

    public AgentRegistry(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // Registry to hold agent metadata, temporrarily in memory
    @Getter
    private final Map<String, AgentMeta> agentRegistry = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void registerAgentsOnStartup() {
        log.info("Registering A2A agents on startup...");
        Map<String, Object> agentBeans = applicationContext.getBeansWithAnnotation(A2AAgent.class);

        for (Map.Entry<String, Object> entry : agentBeans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            Class<?> beanClass = bean.getClass();
            A2AAgent agentAnnotation = beanClass.getAnnotation(A2AAgent.class);

            if (agentAnnotation != null) {
                registerAgent(beanName, bean, agentAnnotation);
            }
        }
    }

    /**
     * Registers an agent bean with its skills into the registry and persists it to the database.
     *
     * @param beanName          The name of the bean in the application context.
     * @param bean              The agent bean instance.
     * @param agentAnnotation   The A2AAgent annotation containing metadata.
     */
    @Transactional
    private void registerAgent(String beanName, Object bean, A2AAgent agentAnnotation) {
        List<SkillMeta> skills = new ArrayList<>();

        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(A2AAgentSkill.class)) {
                A2AAgentSkill skillAnn = method.getAnnotation(A2AAgentSkill.class);
                skills.add(new SkillMeta(skillAnn, method));
            }
        }

        AgentMeta meta = new AgentMeta(agentAnnotation, bean, skills);
        agentRegistry.put(beanName, meta);
        
        log.info("Registered agent: {} with {} skills", agentAnnotation.name(), skills.size());
        List<A2AAgentSkillDTO> skillDTOs = skills.stream()
                .map(skillMeta -> {
                    A2AAgentSkill skillAnnotation = skillMeta.getMethod().getAnnotation(A2AAgentSkill.class);
                    return new A2AAgentSkillDTO(
                            skillAnnotation.id(),
                            skillAnnotation.name(),
                            skillAnnotation.description(),
                            skillAnnotation.tags() != null ? List.of(skillAnnotation.tags()) : List.of());
                })
                .collect(Collectors.toList());

        AgentMetaDTO dto = new AgentMetaDTO(agentAnnotation.name(), skillDTOs);
        ObjectMapper mapper = new ObjectMapper();
        String jmeta = null;
        try {
            jmeta = mapper.writeValueAsString(dto);
        } catch (IOException e) {
            log.error(jmeta + " cannot be serialized to JSON", e);
        }
        if (jmeta == null) {
            log.error("Failed to serialize agent metadata for agent: {}", agentAnnotation.name());
            return;
        }

        AgentEntity entity = AgentEntity.builder()
                .name(agentAnnotation.name())
                .version(agentAnnotation.version())
                .description(agentAnnotation.description())
                .url(agentAnnotation.url())
                .registeredAt(LocalDateTime.now())
                .lastHeartbeat(LocalDateTime.now())
                .active(true)
                .skill(jmeta)
                .build();

        Optional<AgentEntity> existing = agentRepository.findByName(agentAnnotation.name());
        if (existing.isPresent()) {
            entity.setId(existing.get().getId());
            entity.setRegisteredAt(existing.get().getRegisteredAt());
        }

        agentRepository.save(entity);
        log.info("Registered agent: {} with {} skills", agentAnnotation.name(), skills.size());
        log.debug("Agent metadata: {}", jmeta);
    }



    /**
     * Deregister all agents when the application context is closed.
     * This method is called automatically by Spring when the application context is shutting down.
     */
    @Transactional
    public void deregisterAgentsOnContextClose() {
        for (AgentMeta meta : agentRegistry.values()) {
            agentRepository.deleteByName(meta.getName());
            System.out.println("Deregistered agent: " + meta.getName());
        }
        agentRegistry.clear();
    }

    @Getter
    public static class AgentMeta {
        private final String name;
        private final String version;
        private final String description;
        private final String url;
        private final Object bean;
        private final List<SkillMeta> skills;

        public AgentMeta(A2AAgent agentAnn, Object bean, List<SkillMeta> skills) {
            this.name = agentAnn.name();
            this.version = agentAnn.version();
            this.description = agentAnn.description();
            this.url = agentAnn.url();
            this.bean = bean;
            this.skills = skills;
        }
    }

    @Getter
    public static class SkillMeta {
        private final String id;
        private final String name;
        private final String description;
        private final String[] tags;
        private final Method method;

        public SkillMeta(A2AAgentSkill ann, Method method) {
            this.id = ann.id();
            this.name = ann.name();
            this.description = ann.description();
            this.tags = ann.tags();
            this.method = method;
        }
    }
}