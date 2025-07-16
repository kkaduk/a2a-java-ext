package io.a2a.receptionist;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import io.a2a.receptionist.model.AgentEntity;
import io.a2a.receptionist.model.CapabilityQuery;

@Repository
@RequiredArgsConstructor
public class AgentRepositoryImpl implements AgentRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<AgentEntity> searchByCapability(CapabilityQuery query) {
        StringBuilder sql = new StringBuilder("""
                    SELECT * FROM a2a_agents ar
                    WHERE 1=1
                """);

        List<String> conditions = new ArrayList<>();

        // Match skill ID
        if (query.getSkillId() != null && !query.getSkillId().isBlank()) {
            conditions.add("""
                        JSON_EXISTS(ar.skill, '$.skills[*]?(@.id == "%s")')
                    """.formatted(query.getSkillId()));
        }

        // Match required tags (AND / OR)
        if (query.getRequiredTags() != null && !query.getRequiredTags().isEmpty()) {
            boolean matchAll = Boolean.TRUE.equals(query.getMatchAllTags()); // custom flag
            String joiner = matchAll ? " AND " : " OR ";

            String tagConditions = query.getRequiredTags().stream()
                    .map(tag -> """
                                JSON_EXISTS(ar.skill, '$.skills[*]?(@.tags[*] == "%s")')
                            """.formatted(tag))
                    .collect(Collectors.joining(joiner));

            conditions.add("(" + tagConditions + ")");
        }

        // Add all conditions to SQL
        for (String condition : conditions) {
            sql.append(" AND ").append(condition).append("\n");
        }

        // Apply result limit
        if (query.getMaxResults() != null && query.getMaxResults() > 0) {
            sql.append(" FETCH FIRST ").append(query.getMaxResults()).append(" ROWS ONLY");
        }

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), AgentEntity.class);
        return nativeQuery.getResultList();
    }

    @Override
    public Optional<AgentEntity> findByName(String name) {
        String sql = "SELECT * FROM a2a_agents WHERE name = :name FETCH FIRST 1 ROWS ONLY";
        Query query = entityManager.createNativeQuery(sql, AgentEntity.class);
        query.setParameter("name", name);

        List<AgentEntity> resultList = query.getResultList();
        return resultList.isEmpty() ? Optional.empty() : Optional.of(resultList.get(0));
    }

}