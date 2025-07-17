package io.a2a.receptionist;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.a2a.receptionist.service.AgentRegistry;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.InternalError;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Lazy // Important: Lazy initialization to avoid circular dependency
@Slf4j
public class AgentController {

        private final AgentRegistry agentRegistry;

        // Constructor injection instead of field injection
        public AgentController(@Lazy AgentRegistry agentRegistry) {
                this.agentRegistry = agentRegistry;
        }

        // Simple in-memory task storage for demo
        private final Map<String, Task> tasks = new ConcurrentHashMap<>();

        @PostMapping(value = "/agent/message", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Mono<SendMessageResponse> handleSendMessage(@RequestBody SendMessageRequest request) {
                log.info("(KK777) Received message request: {}", request);
                return processMessage(request.getParams())
                                .map(task -> new SendMessageResponse(request.getId(), createResponseMessage(task)))
                                .onErrorReturn(new SendMessageResponse(request.getId(),
                                                new InternalError("Error processing request")));
        }

        @PostMapping(value = "/agent/stream", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Flux<Object> handleStreamingMessage(@RequestBody SendStreamingMessageRequest request) {
                return processMessage(request.getParams())
                                .flatMapMany(task -> {
                                        Flux<Object> statusUpdate = Flux.just(createTaskStatusUpdate(task));
                                        Flux<Object> responseMessage = Flux.just(createResponseMessage(task));
                                        return Flux.concat(statusUpdate, responseMessage);
                                })
                                .onErrorResume(error -> {
                                        log.error("Error processing streaming message", error);

                                        // Create an error task
                                        String taskId = UUID.randomUUID().toString();
                                        String contextId = UUID.randomUUID().toString();

                                        Map<String, Object> errorMetadata = new HashMap<>();
                                        errorMetadata.put("error",
                                                        "Failed to process streaming request: " + error.getMessage());

                                        TaskStatus errorStatus = new TaskStatus(TaskState.FAILED, null,
                                                        LocalDateTime.now());
                                        Task errorTask = new Task.Builder()
                                                        .id(taskId)
                                                        .contextId(contextId)
                                                        .status(errorStatus)
                                                        .metadata(errorMetadata)
                                                        .build();

                                        Flux<Object> errorStatus2 = Flux.just(createTaskStatusUpdate(errorTask));
                                        Flux<Object> errorMessage = Flux
                                                        .just(createErrorMessage(errorTask, error.getMessage()));
                                        return Flux.concat(errorStatus2, errorMessage);
                                });
        }

        private Message createErrorMessage(Task task, String errorMessage) {
                return new Message.Builder()
                                .role(Message.Role.AGENT)
                                .messageId(UUID.randomUUID().toString())
                                .contextId(task.getContextId())
                                .taskId(task.getId())
                                .parts(Collections.singletonList(new TextPart("Error: " + errorMessage)))
                                .build();
        }

        @PostMapping(value = "/agent/tasks/get", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Mono<GetTaskResponse> handleGetTask(@RequestBody GetTaskRequest request) {
                String taskId = request.getParams().id();
                Task task = tasks.get(taskId);

                if (task == null) {
                        return Mono.just(new GetTaskResponse(request.getId(),
                                        new TaskNotFoundError()));
                }

                return Mono.just(new GetTaskResponse(request.getId(), task));
        }

        @PostMapping(value = "/agent/tasks/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Mono<CancelTaskResponse> handleCancelTask(@RequestBody CancelTaskRequest request) {
                String taskId = request.getParams().id();
                Task task = tasks.get(taskId);

                if (task == null) {
                        return Mono.just(new CancelTaskResponse(request.getId(),
                                        new TaskNotFoundError()));
                }

                // Create new task with canceled status (immutable)
                TaskStatus canceledStatus = new TaskStatus(TaskState.CANCELED, null, LocalDateTime.now());
                Task canceledTask = new Task.Builder(task)
                                .status(canceledStatus)
                                .build();

                tasks.put(taskId, canceledTask);

                return Mono.just(new CancelTaskResponse(request.getId(), canceledTask));
        }

        private Mono<Task> processMessage(MessageSendParams params) {
                log.info("(KK777) Processing message: " + params);
                if (params == null || params.message() == null) {
                        return Mono.error(new IllegalArgumentException("Invalid message parameters"));
                }

                Message msg = params.message();
                String skillId = msg.getContextId();

                // Create or find task
                String taskId = msg.getTaskId() != null ? msg.getTaskId() : UUID.randomUUID().toString();
                Task task = tasks.computeIfAbsent(taskId, id -> new Task.Builder()
                                .id(id)
                                .contextId(msg.getContextId() != null ? msg.getContextId()
                                                : UUID.randomUUID().toString())
                                .status(new TaskStatus(TaskState.WORKING, null, LocalDateTime.now()))
                                .history(new ArrayList<>())
                                .build());

                // Extract input from message parts
                String input = extractInputFromMessage(msg);

                // Find and execute skill
                Map<String, AgentRegistry.AgentMeta> agents = agentRegistry.getAgentRegistry();
                log.info("(KK777) expecting skill for agent: " + agents.entrySet().toString() + " for skill: "
                                + skillId);
                for (AgentRegistry.AgentMeta agent : agents.values()) {
                        Optional<AgentRegistry.SkillMeta> skillOpt = agent.getSkills().stream()
                                        .filter(meta -> meta.getId().equals(skillId))
                                        .findFirst();

                        if (skillOpt.isPresent()) {
                                log.info("(KK777) Found skill: " + skillId + " in agent: " + agent.getName());
                                try {
                                        AgentRegistry.SkillMeta skill = skillOpt.get();
                                        Object result;

                                        // Handle different parameter types
                                        Class<?>[] paramTypes = skill.getMethod().getParameterTypes();
                                        if (paramTypes.length == 0) {
                                                result = skill.getMethod().invoke(agent.getBean());
                                                log.info("(KK888-EX-1) Skill " + skillId + " executed with result: "
                                                                + result);
                                        } else if (paramTypes.length == 1) {
                                                result = skill.getMethod().invoke(agent.getBean(), input);
                                                if (result instanceof CompletableFuture) {
                                                        result = ((CompletableFuture<?>) result).get(); // Blocking wait
                                                }
                                                log.info("(KK888-EX-2) Skill " + skillId + " executed with result: "
                                                                + result);
                                        } else {
                                                // For multiple parameters, you might need more sophisticated parameter
                                                // mapping
                                                result = skill.getMethod().invoke(agent.getBean(), input);
                                                log.info(input + " (KK888-EX-3) MP Skill " + skillId
                                                                + " executed with result: " + result);
                                        }

                                        // Create new task with result (immutable)
                                        Map<String, Object> metadata = new HashMap<>();
                                        if (task.getMetadata() != null) {
                                                metadata.putAll(task.getMetadata());
                                        }

                                        log.info("(KK888) Skill " + skillId + " executed with result: "
                                                        + result.toString());
                                        metadata.put("result", result != null ? result.toString() : "null");

                                        // Update task status
                                        TaskStatus completedStatus = new TaskStatus(TaskState.COMPLETED, null,
                                                        LocalDateTime.now());
                                        Task completedTask = new Task.Builder(task)
                                                        .status(completedStatus)
                                                        .metadata(metadata)
                                                        .build();

                                        tasks.put(taskId, completedTask);
                                        log.info("(KK999) Skill " + skillId + " executed successfully with result: "
                                                        + completedTask.getMetadata().get("result"));
                                        return Mono.just(completedTask);
                                } catch (Exception e) {
                                        System.err.println("Error executing skill " + skillId + ": " + e.getMessage());

                                        Map<String, Object> metadata = new HashMap<>();
                                        if (task.getMetadata() != null) {
                                                metadata.putAll(task.getMetadata());
                                        }
                                        metadata.put("error", e.getMessage());

                                        TaskStatus failedStatus = new TaskStatus(TaskState.FAILED, null,
                                                        LocalDateTime.now());
                                        Task failedTask = new Task.Builder(task)
                                                        .status(failedStatus)
                                                        .metadata(metadata)
                                                        .build();

                                        tasks.put(taskId, failedTask);
                                        return Mono.just(failedTask);
                                }
                        }
                }

                Map<String, Object> metadata = new HashMap<>();
                if (task.getMetadata() != null) {
                        metadata.putAll(task.getMetadata());
                }
                metadata.put("error", "Skill not found: " + skillId);

                TaskStatus rejectedStatus = new TaskStatus(TaskState.REJECTED, null, LocalDateTime.now());
                Task rejectedTask = new Task.Builder(task)
                                .status(rejectedStatus)
                                .metadata(metadata)
                                .build();

                tasks.put(taskId, rejectedTask);
                return Mono.just(rejectedTask);
        }

        private String extractInputFromMessage(Message msg) {
                if (msg.getParts() != null && !msg.getParts().isEmpty()) {
                        Part<?> firstPart = msg.getParts().get(0);
                        if (firstPart instanceof TextPart) {
                                return ((TextPart) firstPart).getText();
                        }
                }
                return "";
        }

        private Message createResponseMessage(Task task) {
                String resultText = "Task completed";
                if (task.getMetadata() != null) {
                        Object result = task.getMetadata().get("result");
                        if (result != null) {
                                resultText = result.toString();
                        }
                        Object error = task.getMetadata().get("error");
                        if (error != null) {
                                resultText = "Error: " + error.toString();
                        }
                }

                return new Message.Builder()
                                .role(Message.Role.AGENT)
                                .messageId(UUID.randomUUID().toString())
                                .contextId(task.getContextId())
                                .taskId(task.getId())
                                .parts(Collections.singletonList(new TextPart(resultText)))
                                .build();
        }

        private TaskStatusUpdateEvent createTaskStatusUpdate(Task task) {
                boolean isFinal = task.getStatus().state() == TaskState.COMPLETED ||
                                task.getStatus().state() == TaskState.FAILED ||
                                task.getStatus().state() == TaskState.CANCELED;

                return new TaskStatusUpdateEvent.Builder()
                                .taskId(task.getId())
                                .contextId(task.getContextId())
                                .status(task.getStatus())
                                .isFinal(isFinal)
                                .build();
        }

        @GetMapping(value = "/agent/card", produces = MediaType.APPLICATION_JSON_VALUE)
        public Mono<AgentCard> getAgentCard() {
                List<AgentRegistry.AgentMeta> agents = new ArrayList<>(agentRegistry.getAgentRegistry().values());

                if (agents.isEmpty()) {
                        return Mono.just(createDefaultAgentCard());
                }

                AgentRegistry.AgentMeta primaryAgent = agents.get(0);
                List<AgentSkill> allSkills = agents.stream()
                                .flatMap(agent -> agent.getSkills().stream().map(this::convertToAgentSkill))
                                .collect(Collectors.toList());

                AgentCard card = new AgentCard.Builder()
                                .name(primaryAgent.getName())
                                .version(primaryAgent.getVersion())
                                .description(primaryAgent.getDescription())
                                .url(primaryAgent.getUrl())
                                .skills(allSkills)
                                .defaultInputModes(Collections.singletonList("text"))
                                .defaultOutputModes(Collections.singletonList("text"))
                                .capabilities(createDefaultCapabilities())
                                .provider(createDefaultProvider())
                                .build();

                return Mono.just(card);
        }

        private AgentSkill convertToAgentSkill(AgentRegistry.SkillMeta skillMeta) {
                return new AgentSkill.Builder()
                                .id(skillMeta.getId())
                                .name(skillMeta.getName())
                                .description(skillMeta.getDescription())
                                .tags(Arrays.asList(skillMeta.getTags()))
                                .build();
        }

        private AgentCard createDefaultAgentCard() {
                return new AgentCard.Builder()
                                .name("A2A Agent")
                                .version("1.0")
                                .description("A2A Protocol Agent")
                                .url("http://localhost:8080")
                                .skills(Collections.emptyList())
                                .defaultInputModes(Collections.singletonList("text"))
                                .defaultOutputModes(Collections.singletonList("text"))
                                .capabilities(createDefaultCapabilities())
                                .provider(createDefaultProvider())
                                .build();
        }

        private AgentCapabilities createDefaultCapabilities() {
                return new AgentCapabilities.Builder()
                                .streaming(true)
                                .pushNotifications(false)
                                .stateTransitionHistory(true)
                                .build();
        }

        private AgentProvider createDefaultProvider() {
                return new AgentProvider("A2A System", "http://localhost:8080");
        }
}