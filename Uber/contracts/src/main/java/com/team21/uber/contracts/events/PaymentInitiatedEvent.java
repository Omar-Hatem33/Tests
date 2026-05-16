package com.team21.uber.contracts.events;

public record PaymentInitiatedEvent(Long paymentId, Long rideId, Double amount) {}