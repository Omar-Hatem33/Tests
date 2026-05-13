package com.team21.uber.ride.model;

public enum RideStatus {
    REQUESTED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    REFUNDED
}