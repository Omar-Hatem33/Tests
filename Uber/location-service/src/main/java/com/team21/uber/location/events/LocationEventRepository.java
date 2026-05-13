package com.team21.uber.location.events;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LocationEventRepository extends MongoRepository<LocationEvent, String> {
    List<LocationEvent> findByDriverIdOrderByTimestampDesc(Object driverId);
}
