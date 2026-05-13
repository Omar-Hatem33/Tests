package com.team21.uber.user.dto;

public class UserRideSummaryDTO {

    private Long userId;
    private String name;
    private Integer totalRides;
    private Integer completedRides;
    private Integer cancelledRides;
    private Double totalSpent;
    private Double averageFare;

    public UserRideSummaryDTO(Long userId, String name, Integer totalRides,
                              Integer completedRides, Integer cancelledRides,
                              Double totalSpent, Double averageFare) {
        this.userId = userId;
        this.name = name;
        this.totalRides = totalRides;
        this.completedRides = completedRides;
        this.cancelledRides = cancelledRides;
        this.totalSpent = totalSpent;
        this.averageFare = averageFare;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Integer getTotalRides() { return totalRides; }
    public Integer getCompletedRides() { return completedRides; }
    public Integer getCancelledRides() { return cancelledRides; }
    public Double getTotalSpent() { return totalSpent; }
    public Double getAverageFare() { return averageFare; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long userId;
        private String name;
        private Integer totalRides;
        private Integer completedRides;
        private Integer cancelledRides;
        private Double totalSpent;
        private Double averageFare;

        public Builder userId(Long v) { this.userId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder totalRides(Integer v) { this.totalRides = v; return this; }
        public Builder completedRides(Integer v) { this.completedRides = v; return this; }
        public Builder cancelledRides(Integer v) { this.cancelledRides = v; return this; }
        public Builder totalSpent(Double v) { this.totalSpent = v; return this; }
        public Builder averageFare(Double v) { this.averageFare = v; return this; }

        public UserRideSummaryDTO build() {
            return new UserRideSummaryDTO(userId, name, totalRides, completedRides, cancelledRides, totalSpent, averageFare);
        }
    }
}