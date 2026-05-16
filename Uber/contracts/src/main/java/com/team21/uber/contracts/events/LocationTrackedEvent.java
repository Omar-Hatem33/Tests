package com.team21.uber.contracts.events;

public record LocationTrackedEvent(
        Long driverId,
        Long rideId,
        Double latitude,
        Double longitude
) {}