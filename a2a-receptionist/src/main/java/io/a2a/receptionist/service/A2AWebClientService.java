package io.a2a.receptionist.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for agent-to-agent (A2A) JSON-RPC communication over HTTP using
 * WebFlux.
 */
@Component
@Slf4j
public class A2AWebClientService {

    private final WebClient webClient;

    public A2AWebClientService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Sends an A2A JSON-RPC message/send request to the agent at the provided URL.
     */
    public Mono<SendMessageResponse> sendMessage(String agentUrl, SendMessageRequest request) {
        log.info("Sending message to webclient at URL: " + agentUrl);
        return webClient.post()
                .uri(agentUrl + "/agent/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SendMessageResponse.class)
                // .doOnSuccess(response -> log.info("Response from webclient: " + response.toString()))
                .doOnError(error -> log.error("Error from webclient: " + error.getMessage()));
    }

    /**
     * Sends a streaming message request.
     */
    public Flux<Object> sendStreamingMessage(String agentUrl, SendStreamingMessageRequest request) {
        log.info("Sending streaming message to webclient at URL: " + agentUrl + "/agent/streaming");
        return webClient.post()
                .uri(agentUrl + "/agent/streaming")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(Object.class);
    }

    /**
     * Gets a task by ID.
     */
    public Mono<GetTaskResponse> getTask(String agentUrl, GetTaskRequest request) {
        log.info("Sending message to webclient at URL: " + agentUrl + "/agent/task");
        return webClient.post()
                .uri(agentUrl + "/agent/task")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GetTaskResponse.class);
    }

    /**
     * Cancels a task.
     */
    public Mono<CancelTaskResponse> cancelTask(String agentUrl, CancelTaskRequest request) {
        log.info("Sending message to webclient at URL: " + agentUrl + "/agent/cancel" + " with request: " + request);
        return webClient.post()
                .uri(agentUrl + "/agent/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CancelTaskResponse.class);
    }
}