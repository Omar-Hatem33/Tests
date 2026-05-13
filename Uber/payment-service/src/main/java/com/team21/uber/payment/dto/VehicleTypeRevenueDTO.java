package com.team21.uber.payment.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class VehicleTypeRevenueDTO {

    private String vehicleType;
    private Double baseFareRevenue;
    private Double surgeFeeRevenue;
    private Double totalRevenue;
    private Long   rideCount;

    private VehicleTypeRevenueDTO() {}

    // ─── Getters ──────────────────────────────────────────────────────────────
    public String getVehicleType()     { return vehicleType; }
    public Double getBaseFareRevenue() { return baseFareRevenue; }
    public Double getSurgeFeeRevenue() { return surgeFeeRevenue; }
    public Double getTotalRevenue()    { return totalRevenue; }
    public Long   getRideCount()       { return rideCount; }

    // ─── Builder ──────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String vehicleType;
        private Double baseFareRevenue;
        private Double surgeFeeRevenue;
        private Double totalRevenue;
        private Long   rideCount;

        private Builder() {}

        public Builder vehicleType(String vehicleType) {
            this.vehicleType = vehicleType;
            return this;
        }
        public Builder baseFareRevenue(Double baseFareRevenue) {
            this.baseFareRevenue = baseFareRevenue;
            return this;
        }
        public Builder surgeFeeRevenue(Double surgeFeeRevenue) {
            this.surgeFeeRevenue = surgeFeeRevenue;
            return this;
        }
        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }
        public Builder rideCount(Long rideCount) {
            this.rideCount = rideCount;
            return this;
        }

        public VehicleTypeRevenueDTO build() {
            VehicleTypeRevenueDTO dto = new VehicleTypeRevenueDTO();
            dto.vehicleType     = this.vehicleType;
            dto.baseFareRevenue = this.baseFareRevenue;
            dto.surgeFeeRevenue = this.surgeFeeRevenue;
            dto.totalRevenue    = this.totalRevenue;
            dto.rideCount       = this.rideCount;
            return dto;
        }
    }
}
