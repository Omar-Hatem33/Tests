package com.team21.uber.contracts.events;

public record RideCancelledEvent(Long rideId, Long userId, Long driverId, String reason) {}