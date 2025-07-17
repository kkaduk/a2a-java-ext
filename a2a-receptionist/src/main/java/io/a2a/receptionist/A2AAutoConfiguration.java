package io.a2a.receptionist;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.reactive.function.client.WebClient;

import io.a2a.receptionist.repository.AgentRepository;
import io.a2a.receptionist.repository.AgentRepositoryImpl;
import io.a2a.receptionist.service.A2AWebClientService;
import io.a2a.receptionist.service.AgentRegistry;

@Configuration
@ComponentScan(basePackages = "io.a2a.receptionist")
@EntityScan(basePackages = "io.a2a.receptionist")
@EnableJpaRepositories(basePackages = "io.a2a.receptionist")
public class A2AAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public A2AWebClientService a2aWebClientService(WebClient webClient) {
        return new A2AWebClientService(webClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @DependsOn("agentRepository")
    public AgentRegistry a2aAgentRegistry(AgentRepository agentRepository) {
        return new AgentRegistry(agentRepository);
    }



    @Bean
    @ConditionalOnMissingBean
    @DependsOn("a2aAgentRegistry")
    public AgentController agentController(AgentRegistry a2aAgentRegistry) {
        return new AgentController(a2aAgentRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @DependsOn("agentRepositoryImpl")
    public Receptionist receptionist(AgentRepositoryImpl agentRepositoryImpl,
            A2AWebClientService webClientService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Receptionist(agentRepositoryImpl, webClientService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReceptionistController receptionistController(Receptionist receptionist) {
        return new ReceptionistController(receptionist);
    }
}