package com.team21.uber.driver.dto;

import com.team21.uber.driver.model.DocumentType;
import com.team21.uber.driver.model.DriverStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
    "driverId",
    "driverName",
    "driverStatus",
    "expiredDocuments",
    "expiredCount"
})
public class DriverDocumentAlertDTO {
 
    private Long driverId;
    private String driverName;
    private DriverStatus driverStatus;
    private List<DocumentType> expiredDocuments;
    private int expiredCount;
 
    private DriverDocumentAlertDTO() {}
 
    // Keep the original constructor so existing call sites compile without changes
    public DriverDocumentAlertDTO(Long driverId, String name, DriverStatus status,
                                  List<DocumentType> expiredDocuments, int expiredCount) {
        this.driverId = driverId;
        this.driverName = name;
        this.driverStatus = status;
        this.expiredDocuments = expiredDocuments;
        this.expiredCount = expiredCount;
    }
 
    public Long getDriverId()                          { return driverId; }
    public String getDriverName()                          { return driverName;}
    public DriverStatus getDriverStatus()                    { return driverStatus; }
    public List<DocumentType> getExpiredDocuments() { return expiredDocuments; }
    public int getExpiredCount ()                       { return expiredCount;}
 
    public static Builder builder() {
        return new Builder();
    }
 
    public static class Builder {
        private final DriverDocumentAlertDTO dto = new DriverDocumentAlertDTO();
 
        public Builder driverId(Long driverId) {
            dto.driverId = driverId;
            return this;
        }
 
        public Builder driverName(String name) {
            dto.driverName = name;
            return this;
        }
 
        public Builder status(DriverStatus status) {
            dto.driverStatus = status;
            return this;
        }
 
        public Builder expiredDocuments(List<DocumentType> expiredDocumentTypes) {
            dto.expiredDocuments = expiredDocumentTypes;
            return this;
        }
         public Builder expiredCount(int expiredCount) { 
            dto.expiredCount = expiredCount;
            return this;
        }
 
        public DriverDocumentAlertDTO build() {
            return dto;
        }
    }
}

