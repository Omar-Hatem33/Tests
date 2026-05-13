package com.team21.uber.driver.dto;

public class DriverSearchResultDTO {

    private String driverId;
    private String name;
    private String status;
    private String vehicleType;
    private Double rating;
    private String description;

    public DriverSearchResultDTO() {}

    private DriverSearchResultDTO(Builder b) {
        this.driverId = b.driverId;
        this.name = b.name;
        this.status = b.status;
        this.vehicleType = b.vehicleType;
        this.rating = b.rating;
        this.description = b.description;
    }

    public static Builder builder() { return new Builder(); }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public static class Builder {
        private String driverId;
        private String name;
        private String status;
        private String vehicleType;
        private Double rating;
        private String description;

        public Builder driverId(String driverId) { this.driverId = driverId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder vehicleType(String vehicleType) { this.vehicleType = vehicleType; return this; }
        public Builder rating(Double rating) { this.rating = rating; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public DriverSearchResultDTO build() { return new DriverSearchResultDTO(this); }
    }
}
