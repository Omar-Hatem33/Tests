package com.team21.uber.user.dto;



import java.util.Map;

public class SavedAddressDTO {

    private Long id;
    private String label;
    private String address;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
    private Map<String, Object> metadata;

    public SavedAddressDTO(Long id, String label, String address,
                           Double latitude, Double longitude,
                           Boolean isDefault, Map<String, Object> metadata) {
        this.id = id;
        this.label = label;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isDefault = isDefault;
        this.metadata = metadata;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Boolean getIsDefault() { return isDefault; }
    public Map<String, Object> getMetadata() { return metadata; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long id;
        private String label;
        private String address;
        private Double latitude;
        private Double longitude;
        private Boolean isDefault;
        private Map<String, Object> metadata;

        public Builder id(Long v) { this.id = v; return this; }
        public Builder label(String v) { this.label = v; return this; }
        public Builder address(String v) { this.address = v; return this; }
        public Builder latitude(Double v) { this.latitude = v; return this; }
        public Builder longitude(Double v) { this.longitude = v; return this; }
        public Builder isDefault(Boolean v) { this.isDefault = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public SavedAddressDTO build() {
            return new SavedAddressDTO(id, label, address, latitude, longitude, isDefault, metadata);
        }
    }
}