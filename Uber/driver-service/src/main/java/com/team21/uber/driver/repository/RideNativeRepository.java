package com.team21.uber.driver.repository;

import com.team21.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RideNativeRepository extends Repository<Driver, Long> {

    // Start of S2-F4: Update Driver Availability

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM rides r
                WHERE r.driver_id = :driverId
                  AND r.status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')
            )
            """, nativeQuery = true)
    boolean hasActiveRides(@Param("driverId") Long driverId);

    // End of S2-F4


    // Start of S2-F7: Rate a Driver After Ride

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM rides r
                WHERE r.id = :rideId
            )
            """, nativeQuery = true)
    boolean rideExists(@Param("rideId") Long rideId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM rides r
                WHERE r.id = :rideId
                  AND r.driver_id = :driverId
                  AND r.status = 'COMPLETED'
            )
            """, nativeQuery = true)
    boolean isCompletedRideForDriver(@Param("rideId") Long rideId,
                                     @Param("driverId") Long driverId);

    // End of S2-F7
}