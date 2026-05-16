package com.team21.uber.contracts.events;

public record PaymentFailedEvent(Long paymentId, Long rideId, String reason) {}