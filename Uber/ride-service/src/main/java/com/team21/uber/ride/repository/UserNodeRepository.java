package com.team21.uber.ride.repository;

import java.util.List;
import com.team21.uber.ride.neo4j.DriverNode;
import com.team21.uber.ride.neo4j.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {

    @Query("MATCH (u:User {userId: $userId})-[r:RODE_WITH]->(d:Driver {driverId: $driverId}) " +
           "WHERE $rideId IN r.recordedRideIds " +
           "RETURN COUNT(r) > 0")
    Boolean isRideAlreadyRecorded(@Param("userId") Long userId,
                                  @Param("driverId") Long driverId,
                                  @Param("rideId") Long rideId);

    @Query("MERGE (u:User {userId: $userId}) " +
           "ON CREATE SET u.name = $userName " +
           "MERGE (d:Driver {driverId: $driverId}) " +
           "ON CREATE SET d.name = $driverName, d.vehicleType = $vehicleType " +
           "MERGE (u)-[r:RODE_WITH]->(d) " +
           "ON CREATE SET r.rideCount = 1, r.lastRideDate = $now, r.recordedRideIds = [$rideId] " +
           "ON MATCH SET r.rideCount = r.rideCount + 1, r.lastRideDate = $now, r.recordedRideIds = r.recordedRideIds + $rideId " +
           "RETURN r.rideCount")
    Integer mergeRodeWith(@Param("userId") Long userId,
                      @Param("userName") String userName,
                      @Param("driverId") Long driverId,
                      @Param("driverName") String driverName,
                      @Param("vehicleType") String vehicleType,
                      @Param("rideId") Long rideId,
                      @Param("now") LocalDateTime now);

    @Query("MATCH (u:User {userId: $userId})-[r:RODE_WITH]->(d:Driver {driverId: $driverId}) " +
           "RETURN r.rideCount")
    Integer getRideCount(@Param("userId") Long userId, @Param("driverId") Long driverId);

    @Query("""
    MATCH (u:User {userId: $userId})-[:RODE_WITH]->(d:Driver)
    WITH u, collect(d) as userDrivers
    
    MATCH (other:User)-[:RODE_WITH]->(d2:Driver)
    WHERE other.userId <> $userId
    
    WITH other, collect(d2) as otherDrivers, userDrivers
    
    WHERE size([x IN userDrivers WHERE x IN otherDrivers]) > 0
    
    MATCH (other)-[:RODE_WITH]->(recommended:Driver)
    
    WHERE NOT (u)-[:RODE_WITH]->(recommended)
    
    RETURN DISTINCT recommended LIMIT 10
""")
    List<DriverNode> recommendDrivers(@Param("userId") Long userId);
}
