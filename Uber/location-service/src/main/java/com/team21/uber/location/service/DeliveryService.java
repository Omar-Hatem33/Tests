package com.team21.uber.location.service;

import com.team21.uber.location.dto.*;
import com.team21.uber.location.events.EventPublisher;
import com.team21.uber.location.exception.BadRequestException;
import com.team21.uber.location.exception.ResourceNotFoundException;
import com.team21.uber.location.model.Location;
import com.team21.uber.location.repository.DeliveryRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String AVAILABLE_STATUS = "AVAILABLE";

    private final DeliveryRepository deliveryRepository;
    private final EventPublisher eventPublisher;

    public DeliveryService(DeliveryRepository deliveryRepository, EventPublisher eventPublisher) {
        this.deliveryRepository = deliveryRepository;
        this.eventPublisher = eventPublisher;
    }

    private void publish(String action, Long driverId, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("driverId", driverId == null ? null : String.valueOf(driverId));
            payload.put("action", action);
            if (extra != null) payload.putAll(extra);
            eventPublisher.notifyObservers(action, payload);
        } catch (Exception ex) {
            log.warn("Failed to publish event {}: {}", action, ex.getMessage());
        }
    }

    public List<DeliveryResponse> getAllDeliveries() {
        return deliveryRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))
                .stream()
                .map(DeliveryResponse::fromEntity)
                .toList();
    }

    public DeliveryResponse getDelivery(Long id) {
        return DeliveryResponse.fromEntity(findDelivery(id));
    }

    @Transactional
    public DeliveryResponse createDeliveryForDriver(Long driverId, DeliveryRequest request) {
        validateDriverId(driverId);
        ensureDriverExists(driverId);
        validateRequest(request);

        Location delivery = new Location();
        applyRequest(delivery, driverId, request, false);
        DeliveryResponse response = DeliveryResponse.fromEntity(deliveryRepository.save(delivery));
        publish("DELIVERY_CREATED", driverId, Map.of(
                "deliveryId", response.getId() == null ? "" : String.valueOf(response.getId()),
                "latitude", request.getLatitude(),
                "longitude", request.getLongitude()));
        return response;
    }

    @Transactional
    public DeliveryResponse updateDelivery(Long id, DeliveryRequest request) {
        validateRequest(request);
        Location delivery = findDelivery(id);
        applyRequest(delivery, delivery.getDriverId(), request, true);
        DeliveryResponse response = DeliveryResponse.fromEntity(deliveryRepository.save(delivery));
        publish("DELIVERY_UPDATED", delivery.getDriverId(), Map.of(
                "deliveryId", String.valueOf(id),
                "latitude", request.getLatitude(),
                "longitude", request.getLongitude()));
        return response;
    }

    @Transactional
    public void deleteDelivery(Long id) {
        Location delivery = findDelivery(id);
        Long driverId = delivery.getDriverId();
        deliveryRepository.delete(delivery);
        publish("DELIVERY_DELETED", driverId, Map.of("deliveryId", String.valueOf(id)));
    }

    public DeliveryResponse getLatestDelivery(Long driverId) {
        validateDriverId(driverId);
        ensureDriverExists(driverId);
        Location delivery = deliveryRepository.findTopByDriverIdOrderByTimestampDesc(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("No locations found for driver id: " + driverId));
        return DeliveryResponse.fromEntity(delivery);
    }

    public List<NearbyDriverDTO> findNearbyDeliveries(double latitude, double longitude, double radiusKm) {
        validateCoordinates(latitude, longitude);
        if (radiusKm < 0) {
            throw new BadRequestException("radiusKm must be greater than or equal to 0");
        }

        return latestLocationsByDriver(deliveryRepository.findAllByOrderByTimestampDesc())
                .stream()
                .filter(location -> AVAILABLE_STATUS.equalsIgnoreCase(deliveryRepository.findDriverStatus(location.getDriverId())))
                .map(location -> toNearbyDto(location, calculateDistanceKm(latitude, longitude, location.getLatitude(), location.getLongitude())))
                .filter(location -> location.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(NearbyDriverDTO::getDistanceKm))
                .toList();
    }

    private Location findDelivery(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
    }

    private void applyRequest(Location delivery, Long driverId, DeliveryRequest request, boolean preserveExistingMetadata) {
        delivery.setDriverId(driverId);
        delivery.setLatitude(request.getLatitude());
        delivery.setLongitude(request.getLongitude());
        if (request.getMetadata() != null) {
            delivery.setMetadata(copyMetadata(request.getMetadata()));
        } else if (!preserveExistingMetadata || delivery.getMetadata() == null) {
            delivery.setMetadata(new HashMap<>());
        }
    }

    private void applyRequest(Location delivery, Long driverId, DeliveryRequest request, boolean preserveExistingMetadata, LocalDateTime timestamp) {
        delivery.setDriverId(driverId);
        delivery.setLatitude(request.getLatitude());
        delivery.setLongitude(request.getLongitude());
        delivery.setTimestamp(timestamp);
        if (request.getMetadata() != null) {
            delivery.setMetadata(copyMetadata(request.getMetadata()));
        } else if (!preserveExistingMetadata || delivery.getMetadata() == null) {
            delivery.setMetadata(new HashMap<>());
        }
    }

    private void validateLocationRequest(DeliveryRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new BadRequestException("latitude and longitude are required");
        }
        validateCoordinates(request.getLatitude(), request.getLongitude());
    }



    private void validateDriverId(Long driverId) {
        if (driverId == null || driverId <= 0) {
            throw new BadRequestException("driverId must be a positive number");
        }
    }

    private void ensureDriverExists(Long driverId) {
        if (!deliveryRepository.driverExists(driverId)) {
            throw new ResourceNotFoundException("Driver not found with id: " + driverId);
        }
    }

    private void validateRequest(DeliveryRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new BadRequestException("latitude and longitude are required");
        }
        validateCoordinates(request.getLatitude(), request.getLongitude());
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new BadRequestException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new BadRequestException("Longitude must be between -180 and 180");
        }
    }

    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }
    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
    }

    private void validateOperator(String operator) {
        validateText(operator, "operator");
        List<String> supportedOperators = List.of("eq", "gt", "lt");
        if (!supportedOperators.contains(operator.toLowerCase())) {
            throw new BadRequestException("operator must be one of eq, gt, lt");
        }
    }

    private double parseNumeric(String value, String fieldName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new BadRequestException(fieldName + " must be numeric");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("startDate and endDate are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must be before or equal to endDate");
        }
    }

    private double calculateDistanceKm(double sourceLatitude, double sourceLongitude, double targetLatitude, double targetLongitude) {
        double latDifference = targetLatitude - sourceLatitude;
        double lonDifference = targetLongitude - sourceLongitude;
        return Math.sqrt((latDifference * latDifference) + (lonDifference * lonDifference)) * 111;
    }

    private NearbyDriverDTO toNearbyDto(Location delivery, double distanceKm) {
        NearbyDriverDTO dto = new NearbyDriverDTO();
        dto.setDriverId(delivery.getDriverId());
        dto.setDriverName(deliveryRepository.findDriverName(delivery.getDriverId()));
        dto.setLatitude(delivery.getLatitude());
        dto.setLongitude(delivery.getLongitude());
        dto.setDistanceKm(distanceKm);
        return dto;
    }
    @Transactional
    public BatchDeliveryResponse createBatch(BatchDeliveryUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        validateDriverId(request.getDriverId());
        ensureDriverExists(request.getDriverId());

        if (request.getLocations() == null || request.getLocations().isEmpty()) {
            throw new BadRequestException("At least one location point is required");
        }

        List<DeliveryResponse> savedLocations = new ArrayList<>();
        LocalDateTime baseTimestamp = LocalDateTime.now();

        for (int index = 0; index < request.getLocations().size(); index++) {
            DeliveryRequest update = request.getLocations().get(index);
            validateLocationRequest(update);

            Location delivery = new Location();
            applyRequest(delivery, request.getDriverId(), update, false, baseTimestamp.plusSeconds(index));
            savedLocations.add(DeliveryResponse.fromEntity(deliveryRepository.save(delivery)));
        }

        BatchDeliveryResponse response = new BatchDeliveryResponse();
        response.setCount(savedLocations.size());
        response.setLocations(savedLocations);
        publish("DELIVERY_BATCH_CREATED", request.getDriverId(),
                Map.of("count", savedLocations.size()));
        return response;
    }

    public List<DeliveryResponse> searchByMetadata(String key, String operator, String value) {
        validateText(key, "key");
        validateText(value, "value");
        validateOperator(operator);

        if (!"eq".equalsIgnoreCase(operator)) {
            parseNumeric(value, "value");
        }

        return deliveryRepository.searchByMetadata(key, operator, value)
                .stream()
                .map(DeliveryResponse::fromEntity)
                .toList();
    }

    public List<DeliveryResponse> getLocationHistory(LocalDate startDate, LocalDate endDate, Long driverId) {
        validateDateRange(startDate, endDate);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Location> locations;
        if (driverId != null) {
            validateDriverId(driverId);
            ensureDriverExists(driverId);
            locations = deliveryRepository.findByDriverIdAndTimestampBetweenOrderByTimestampAsc(driverId, start, end);
        } else {
            locations = deliveryRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
        }

        return locations.stream().map(DeliveryResponse::fromEntity).toList();
    }

    private List<Location> latestLocationsByDriver(List<Location> locations) {
        Map<Long, Location> latestByDriver = new LinkedHashMap<>();
        for (Location location : locations) {
            if (location.getDriverId() != null && !latestByDriver.containsKey(location.getDriverId())) {
                latestByDriver.put(location.getDriverId(), location);
            }
        }
        return latestByDriver.values().stream().filter(Objects::nonNull).toList();
    }

    @Transactional
    public PurgeResponse purgeOldDeliveries(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new BadRequestException("olderThanDays must be greater than or equal to 0");
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        long deletedCount = deliveryRepository.countByTimestampBefore(cutoff);

        if (deletedCount > 0) {
            deliveryRepository.deleteExpiredLocations(cutoff);
        }

        publish("DELIVERIES_PURGED", null, Map.of(
                "olderThanDays", olderThanDays,
                "deletedCount", deletedCount));
        return new PurgeResponse(deletedCount);
    }

    private double extractNumericMetadata(Map<String, Object> metadata, String key) { //f8
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return 0.0;
        }

        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String text && !text.isBlank()) {
            return parseNumeric(text, key);
        }

        return 0.0;
    }

    public DriverMovementSummaryDTO getDriverSummary(Long driverId, LocalDate startDate, LocalDate endDate) {
        validateDriverId(driverId);
        ensureDriverExists(driverId);

        List<Location> deliveries;
        if (startDate != null && endDate != null) {
            validateDateRange(startDate, endDate);
            deliveries = deliveryRepository.findByDriverIdAndTimestampBetweenOrderByTimestampAsc(
                    driverId,
                    startDate.atStartOfDay(),
                    endDate.atTime(LocalTime.MAX)
            );
        } else {
            deliveries = deliveryRepository.findByDriverIdOrderByTimestampAsc(driverId);
        }

        String driverName = deliveryRepository.findDriverName(driverId);
        DriverMovementSummaryDTO summary = new DriverMovementSummaryDTO();
        summary.setDriverId(driverId);
        summary.setDriverName(driverName);

        if (deliveries.isEmpty()) {
            summary.setTotalLocationPoints(0);
            summary.setAverageSpeed(0.0);
            summary.setMaxSpeed(0.0);
            summary.setFirstTimestamp(null);
            summary.setLastTimestamp(null);
            return summary;
        }

        double totalSpeed = 0.0;
        double maxSpeed = 0.0;

        for (Location delivery : deliveries) {
            double speed = extractNumericMetadata(delivery.getMetadata(), "speed");
            totalSpeed += speed;
            maxSpeed = Math.max(maxSpeed, speed);
        }

        summary.setTotalLocationPoints(deliveries.size());
        summary.setAverageSpeed(totalSpeed / deliveries.size());
        summary.setMaxSpeed(maxSpeed);
        summary.setFirstTimestamp(deliveries.getFirst().getTimestamp());
        summary.setLastTimestamp(deliveries.getLast().getTimestamp());
        return summary;
    }

    public List<StationaryDriverDTO> findDelayedDeliveries(double maxEstimatedArrival, long sinceMinutes) {
        if (maxEstimatedArrival < 0) {
            throw new BadRequestException("maxSpeed must be greater than or equal to 0");
        }

        if (sinceMinutes <= 0) {
            throw new BadRequestException("sinceMinutes must be greater than 0");
        }

        LocalDateTime since = LocalDateTime.now().minusMinutes(sinceMinutes);
        return latestLocationsByDriver(deliveryRepository.findAllByOrderByTimestampDesc())
                .stream()
                .filter(location -> location.getTimestamp() != null && !location.getTimestamp().isBefore(since))
                .filter(location -> extractNumericMetadata(location.getMetadata(), "speed") <= maxEstimatedArrival)
                .map(this::toDelayedDto)
                .toList();
    }

    private StationaryDriverDTO toDelayedDto(Location delivery) {
        StationaryDriverDTO dto = new StationaryDriverDTO();
        dto.setDriverId(delivery.getDriverId());
        dto.setDriverName(deliveryRepository.findDriverName(delivery.getDriverId()));
        dto.setLatitude(delivery.getLatitude());
        dto.setLongitude(delivery.getLongitude());
        dto.setLastSpeed(extractNumericMetadata(delivery.getMetadata(), "speed"));
        dto.setLastUpdated(delivery.getTimestamp());
        return dto;
    }
}