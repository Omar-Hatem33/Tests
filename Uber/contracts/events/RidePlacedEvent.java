package com.team21.uber.contracts.events;

public record RidePlacedEvent(Long rideId, Long userId, Long driverId) {}