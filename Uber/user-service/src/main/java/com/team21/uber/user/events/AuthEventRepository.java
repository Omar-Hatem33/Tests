package com.team21.uber.user.events;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {

    // userId is Long (matches AuthEvent.userId field type)
    List<AuthEvent> findByUserIdOrderByTimestampDesc(Long userId);
}