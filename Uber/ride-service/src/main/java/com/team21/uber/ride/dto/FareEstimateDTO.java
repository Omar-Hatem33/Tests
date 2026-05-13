package com.team21.uber.ride.dto;

public class FareEstimateDTO {
    private Double estimatedDistance;
    private Double estimatedDuration;
    private Double estimatedFare;
    private Double surgeMultiplier;

    public FareEstimateDTO() {}

    public FareEstimateDTO(Double estimatedDistance, Double estimatedDuration,
                           Double estimatedFare, Double surgeMultiplier) {
        this.estimatedDistance = estimatedDistance;
        this.estimatedDuration = estimatedDuration;
        this.estimatedFare = estimatedFare;
        this.surgeMultiplier = surgeMultiplier;
    }

    public Double getEstimatedDistance() { return estimatedDistance; }
    public void setEstimatedDistance(Double estimatedDistance) { this.estimatedDistance = estimatedDistance; }

    public Double getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(Double estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public Double getEstimatedFare() { return estimatedFare; }
    public void setEstimatedFare(Double estimatedFare) { this.estimatedFare = estimatedFare; }

    public Double getSurgeMultiplier() { return surgeMultiplier; }
    public void setSurgeMultiplier(Double surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Double estimatedDistance;
        private Double estimatedDuration;
        private Double estimatedFare;
        private Double surgeMultiplier;

        public Builder estimatedDistance(Double v) { this.estimatedDistance = v; return this; }
        public Builder estimatedDuration(Double v) { this.estimatedDuration = v; return this; }
        public Builder estimatedFare(Double v) { this.estimatedFare = v; return this; }
        public Builder surgeMultiplier(Double v) { this.surgeMultiplier = v; return this; }

        public FareEstimateDTO build() {
            return new FareEstimateDTO(estimatedDistance, estimatedDuration, estimatedFare, surgeMultiplier);
        }
    }
}
