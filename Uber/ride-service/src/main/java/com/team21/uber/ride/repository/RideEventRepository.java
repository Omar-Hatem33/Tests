package com.team21.uber.ride.repository;

import com.team21.uber.ride.event.RideEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RideEventRepository extends MongoRepository<RideEvent, String> {
}
