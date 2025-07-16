package io.a2a.receptionist;

import java.util.List;
import java.util.Optional;

import io.a2a.receptionist.model.AgentEntity;
import io.a2a.receptionist.model.CapabilityQuery;

public interface AgentRepositoryCustom {
    List<AgentEntity> searchByCapability(CapabilityQuery query);

    Optional<AgentEntity> findByName(String name);
}
