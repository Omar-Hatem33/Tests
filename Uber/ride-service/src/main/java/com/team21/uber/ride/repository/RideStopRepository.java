package com.team21.uber.ride.repository;

import com.team21.uber.ride.model.RideStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideStopRepository extends JpaRepository<RideStop, Long> {

    // S3-F8: get the current max stop order for a ride (returns 0 if no stops exist)
    @Query("SELECT COALESCE(MAX(rs.stopOrder), 0) FROM RideStop rs WHERE rs.ride.id = :rideId")
    int findMaxStopOrderByRideId(@Param("rideId") Long rideId);

    // S3-F9: get all stops for a ride ordered by stopOrder ascending
    @Query("SELECT rs FROM RideStop rs WHERE rs.ride.id = :rideId ORDER BY rs.stopOrder ASC")
    List<RideStop> findByRideIdOrderByStopOrderAsc(@Param("rideId") Long rideId);
}
