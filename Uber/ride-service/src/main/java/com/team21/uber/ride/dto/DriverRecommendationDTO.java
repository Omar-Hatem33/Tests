//package com.team21.uber.ride.dto;
//
//public class DriverRecommendationDTO {
//    private Long driverId;
//    private String name;
//    private String vehicleType;
//    private Long score;
//
//    // Default constructor
//    public DriverRecommendationDTO() {}
//
//    // All-args constructor
//    public DriverRecommendationDTO(Long driverId, String name, String vehicleType, Long score) {
//        this.driverId = driverId;
//        this.name = name;
//        this.vehicleType = vehicleType;
//        this.score = score;
//    }
//
//    // Getters and Setters
//    public Long getDriverId() { return driverId; }
//    public void setDriverId(Long driverId) { this.driverId = driverId; }
//
//    public String getName() { return name; }
//    public void setName(String name) { this.name = name; }
//
//    public String getVehicleType() { return vehicleType; }
//    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
//
//    public Long getScore() { return score; }
//    public void setScore(Long score) { this.score = score; }
//
//    // Builder pattern
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    public static class Builder {
//        private Long driverId;
//        private String name;
//        private String vehicleType;
//        private Long score;
//
//        public Builder driverId(Long driverId) {
//            this.driverId = driverId;
//            return this;
//        }
//
//        public Builder name(String name) {
//            this.name = name;
//            return this;
//        }
//
//        public Builder vehicleType(String vehicleType) {
//            this.vehicleType = vehicleType;
//            return this;
//        }
//
//        public Builder score(Long score) {
//            this.score = score;
//            return this;
//        }
//
//        public DriverRecommendationDTO build() {
//            return new DriverRecommendationDTO(driverId, name, vehicleType, score);
//        }
//    }
//}