package com.team21.uber.driver.dto;

public class DriverRatingRequest {

    private Long rideId;
    private Integer rating;

    public DriverRatingRequest() {
    }

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}