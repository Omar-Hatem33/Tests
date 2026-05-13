package com.team21.uber.ride.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class RideAnalyticsRepositoryImpl implements RideAnalyticsRepository {

    private final JdbcTemplate jdbc;

    public RideAnalyticsRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countTotalRides(LocalDateTime start, LocalDateTime end) {
        Long result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rides WHERE requested_at BETWEEN ? AND ?",
                Long.class, start, end
        );
        return result == null ? 0 : result;
    }

    @Override
    public long countCompletedRides(LocalDateTime start, LocalDateTime end) {
        Long result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rides WHERE status='COMPLETED' AND requested_at BETWEEN ? AND ?",
                Long.class, start, end
        );
        return result == null ? 0 : result;
    }

    @Override
    public double sumRevenue(LocalDateTime start, LocalDateTime end) {
        Double result = jdbc.queryForObject(
                "SELECT COALESCE(SUM(p.amount),0) FROM payments p " +
                        "JOIN rides r ON p.ride_id = r.id " +
                        "WHERE r.status='COMPLETED' AND r.requested_at BETWEEN ? AND ?",
                Double.class, start, end
        );
        return result == null ? 0 : result;
    }

    @Override
    public List<Map<String, Object>> countByStatus(LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForList(
                "SELECT status, COUNT(*) as count FROM rides WHERE requested_at BETWEEN ? AND ? GROUP BY status",
                start, end
        );
    }
}