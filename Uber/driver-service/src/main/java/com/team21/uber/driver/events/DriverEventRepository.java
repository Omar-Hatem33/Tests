package com.team21.uber.driver.events;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DriverEventRepository extends MongoRepository<DriverEvent, String> {
    List<DriverEvent> findByDriverIdOrderByTimestampDesc(Long driverId);
}
