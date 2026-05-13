package com.team21.uber.location.repository;

import com.team21.uber.location.model.Location;

import java.util.List;

public interface DeliveryQueryRepository {

    boolean driverExists(Long driverId);

    String findDriverName(Long driverId);

    String findDriverStatus(Long driverId);

    List<Location> searchByMetadata(String key, String operator, String value);
}