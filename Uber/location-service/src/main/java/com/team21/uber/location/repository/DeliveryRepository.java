package com.team21.uber.location.repository;

import com.team21.uber.location.model.Location;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Location, Long>, DeliveryQueryRepository {

    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);

    List<Location> findAllByOrderByTimestampDesc();

    List<Location> findByDriverIdAndTimestampBetweenOrderByTimestampAsc(Long driverId, LocalDateTime start, LocalDateTime end);

    List<Location> findByTimestampBetweenOrderByTimestampAsc(LocalDateTime start, LocalDateTime end);

    List<Location> findByDriverIdOrderByTimestampAsc(Long driverId);

    long countByTimestampBefore(LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM locations WHERE timestamp < :cutoff", nativeQuery = true)
    int deleteExpiredLocations(@Param("cutoff") LocalDateTime cutoff);
}
