package com.team21.uber.location.repository;

import com.team21.uber.location.model.Location;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeliveryQueryRepositoryImpl implements DeliveryQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean driverExists(Long driverId) {
        if (!tableExists("drivers")) {
            return true;
        }
        return countByReferenceTable("drivers", driverId) > 0;
    }

    @Override
    public String findDriverName(Long driverId) {
        if (!tableExists("drivers")) {
            return "Driver " + driverId;
        }

        @SuppressWarnings("unchecked")
        List<String> names = entityManager.createNativeQuery("SELECT name FROM drivers WHERE id = :driverId")
                .setParameter("driverId", driverId)
                .getResultList();
        return names.isEmpty() ? "Driver " + driverId : names.getFirst();
    }

    @Override
    public String findDriverStatus(Long driverId) {
        if (!tableExists("drivers")) {
            return "AVAILABLE";
        }

        @SuppressWarnings("unchecked")
        List<String> statuses = entityManager.createNativeQuery("SELECT CAST(status AS TEXT) FROM drivers WHERE id = :driverId")
                .setParameter("driverId", driverId)
                .getResultList();
        return statuses.isEmpty() ? null : statuses.getFirst();
    }
    @Override
    public List<Location> searchByMetadata(String key, String operator, String value) {
        String query = switch (operator.toLowerCase()) {
            case "eq" -> """
                    SELECT *
                    FROM locations d
                    WHERE d.metadata IS NOT NULL
                      AND d.metadata ->> :key = :value
                    ORDER BY d.timestamp DESC
                    """;
            case "gt" -> """
                    SELECT *
                    FROM locations d
                    WHERE d.metadata IS NOT NULL
                      AND CAST(d.metadata ->> :key AS DOUBLE PRECISION) > :numericValue
                    ORDER BY d.timestamp DESC
                    """;
            case "lt" -> """
                    SELECT *
                    FROM locations d
                    WHERE d.metadata IS NOT NULL
                      AND CAST(d.metadata ->> :key AS DOUBLE PRECISION) < :numericValue
                    ORDER BY d.timestamp DESC
                    """;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };

        Query nativeQuery = entityManager.createNativeQuery(query, Location.class);
        nativeQuery.setParameter("key", key);

        if ("eq".equalsIgnoreCase(operator)) {
            nativeQuery.setParameter("value", value);
        } else {
            nativeQuery.setParameter("numericValue", Double.parseDouble(value));
        }

        @SuppressWarnings("unchecked")
        List<Location> deliveries = nativeQuery.getResultList();
        return deliveries;
    }
    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name = :tableName
                        """)
                .setParameter("tableName", tableName)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private long countByReferenceTable(String tableName, Long referenceId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM " + tableName + " WHERE id = :referenceId")
                .setParameter("referenceId", referenceId)
                .getSingleResult();
        return count.longValue();
    }
}