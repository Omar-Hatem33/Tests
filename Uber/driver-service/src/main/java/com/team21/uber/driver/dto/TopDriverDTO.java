package com.team21.uber.driver.dto;

public class TopDriverDTO {
 
    private Long driverId;
    private String name;
    private Double rating;
    private Long totalRides;
 
    private TopDriverDTO() {}
 
    public TopDriverDTO(Long driverId, String name, Double rating, Long totalRides) {
        this.driverId = driverId;
        this.name = name;
        this.rating = rating;
        this.totalRides = totalRides;
    }
 
    public Long getDriverId()    { return driverId; }
    public String getName()      { return name; }
    public Double getRating()    { return rating; }
    public Long getTotalRides()  { return totalRides; }
 
    public static Builder builder() {
        return new Builder();
    }
 
    public static class Builder {
        private final TopDriverDTO dto = new TopDriverDTO();
 
        public Builder driverId(Long driverId) {
            dto.driverId = driverId;
            return this;
        }
 
        public Builder name(String name) {
            dto.name = name;
            return this;
        }
 
        public Builder rating(Double rating) {
            dto.rating = rating;
            return this;
        }
 
        public Builder totalRides(Long totalRides) {
            dto.totalRides = totalRides;
            return this;
        }
 
        public TopDriverDTO build() {
            return dto;
        }
    }
}
