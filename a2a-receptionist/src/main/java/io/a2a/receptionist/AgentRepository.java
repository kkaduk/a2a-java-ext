
package io.a2a.receptionist;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import io.a2a.receptionist.model.AgentEntity;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, Long> {
    Optional<AgentEntity> findByName(String name);
    void deleteByName(String name);
}