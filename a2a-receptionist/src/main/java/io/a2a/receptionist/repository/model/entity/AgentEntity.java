package io.a2a.receptionist.repository.model.entity;


import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "a2a_agents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false)
    private String version;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private String url;
    
    @Column(name = "registered_at")
    private LocalDateTime registeredAt;
    
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;
    
    @Builder.Default
    @Column
    private boolean active = true;

    @Column(name = "skill")
    private String skill;
}