package io.a2a.receptionist.repository;

import java.util.List;
import java.util.Optional;

import io.a2a.receptionist.model.A2ASkillQuery;
import io.a2a.receptionist.repository.model.entity.AgentEntity;

public interface AgentRepositoryCustom {
    List<AgentEntity> searchByCapability(A2ASkillQuery query);

    Optional<AgentEntity> findByName(String name);
}
