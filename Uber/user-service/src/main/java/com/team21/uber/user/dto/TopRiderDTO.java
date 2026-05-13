package com.team21.uber.user.dto;

public class TopRiderDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long rideCount;

    private TopRiderDTO() {}

    // Getters
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Double getTotalSpent() { return totalSpent; }
    public Long getRideCount() { return rideCount; }

    // Setters
    public void setUserId(Long userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public void setRideCount(Long rideCount) { this.rideCount = rideCount; }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private String name;
        private Double totalSpent;
        private Long rideCount;

        private Builder() {}

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalSpent(Double totalSpent) {
            this.totalSpent = totalSpent;
            return this;
        }

        public Builder rideCount(Long rideCount) {
            this.rideCount = rideCount;
            return this;
        }

        public TopRiderDTO build() {
            TopRiderDTO dto = new TopRiderDTO();
            dto.userId = this.userId;
            dto.name = this.name;
            dto.totalSpent = this.totalSpent;
            dto.rideCount = this.rideCount;
            return dto;
        }
    }
}