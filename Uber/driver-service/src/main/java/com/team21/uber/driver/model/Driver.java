package com.team21.uber.driver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status;

    private Double rating = 0.0;

    private Integer totalRatings = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> vehicleDetails = new HashMap<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "driver", cascade = CascadeType.ALL)
    private List<DriverDocument> driverDocuments;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Driver() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }

    public Map<String, Object> getVehicleDetails() {
        return vehicleDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<DriverDocument> getDriverDocuments() {
        return driverDocuments;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }

    public void setVehicleDetails(Map<String, Object> vehicleDetails) {
        this.vehicleDetails = vehicleDetails;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDriverDocuments(List<DriverDocument> driverDocuments) {
        this.driverDocuments = driverDocuments;
    }
}