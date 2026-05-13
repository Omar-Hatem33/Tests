package com.team21.uber.location.config;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
@ConditionalOnProperty(prefix = "spring.cassandra", name = "contact-points")
public class CassandraKeyspaceConfig {

    private static final Logger log = LoggerFactory.getLogger(CassandraKeyspaceConfig.class);
    private static final long WAIT_BUDGET_MS = 60_000L;
    private static final long RETRY_DELAY_MS = 2_000L;

    @Value("${spring.cassandra.contact-points:cassandra}")
    private String contactPoints;

    @Value("${spring.cassandra.port:9042}")
    private int port;

    @Value("${spring.cassandra.local-datacenter:datacenter1}")
    private String localDatacenter;

    @Value("${spring.cassandra.keyspace-name:uberks}")
    private String keyspace;

    @PostConstruct
    public void ensureKeyspace() {
        long deadline = System.currentTimeMillis() + WAIT_BUDGET_MS;
        Exception lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try (CqlSession session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress(contactPoints, port))
                    .withLocalDatacenter(localDatacenter)
                    .build()) {
                session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace
                        + " WITH replication = {'class':'SimpleStrategy','replication_factor':1}");
                ensureTrackingSchema(session);
                log.info("Cassandra keyspace '{}' ready", keyspace);
                return;
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Cassandra not ready ({}): {}", contactPoints, ex.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Cassandra keyspace '{}' could not be ensured within {} ms",
                keyspace, WAIT_BUDGET_MS, lastError);
    }

    private void ensureTrackingSchema(CqlSession session) {
        session.execute("CREATE TABLE IF NOT EXISTS " + keyspace + ".location_tracking_events ("
                + "driver_id bigint, "
                + "timestamp timestamp, "
                + "tracking_id uuid, "
                + "latitude double, "
                + "longitude double, "
                + "accuracy double, "
                + "speed double, "
                + "heading double, "
                + "notes text, "
                + "PRIMARY KEY ((driver_id), timestamp, tracking_id)"
                + ") WITH CLUSTERING ORDER BY (timestamp DESC, tracking_id ASC)");
        // If a previous run created driver_id as text, drop and recreate so test's bigint bind matches.
        ensureDriverIdBigint(session);
        addColumnIfMissing(session, "speed", "double");
        addColumnIfMissing(session, "heading", "double");
        addColumnIfMissing(session, "notes", "text");
        addColumnIfMissing(session, "accuracy", "double");
    }

    private void addColumnIfMissing(CqlSession session, String column, String cqlType) {
        try {
            session.execute("ALTER TABLE " + keyspace + ".location_tracking_events ADD " + column + " " + cqlType);
        } catch (Exception ignored) {
            // column already present
        }
    }

    private void ensureDriverIdBigint(CqlSession session) {
        try {
            var row = session.execute("SELECT type FROM system_schema.columns WHERE keyspace_name='" + keyspace
                    + "' AND table_name='location_tracking_events' AND column_name='driver_id'").one();
            if (row == null) return;
            String type = row.getString("type");
            if (type == null || "bigint".equalsIgnoreCase(type)) return;
            log.warn("driver_id column is {} (need bigint); dropping and recreating location_tracking_events", type);
            session.execute("DROP TABLE IF EXISTS " + keyspace + ".location_tracking_events");
            session.execute("CREATE TABLE IF NOT EXISTS " + keyspace + ".location_tracking_events ("
                    + "driver_id bigint, "
                    + "timestamp timestamp, "
                    + "tracking_id uuid, "
                    + "latitude double, "
                    + "longitude double, "
                    + "accuracy double, "
                    + "speed double, "
                    + "heading double, "
                    + "notes text, "
                    + "PRIMARY KEY ((driver_id), timestamp, tracking_id)"
                    + ") WITH CLUSTERING ORDER BY (timestamp DESC, tracking_id ASC)");
        } catch (Exception ex) {
            log.warn("Could not verify/repair driver_id column type: {}", ex.getMessage());
        }
    }
}
