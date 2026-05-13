package com.team21.uber.contracts.events;

public record DriverDocumentVerifiedEvent(Long driverId, Long documentId, Long verifiedBy) {}