package com.team21.uber.driver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "driver_documents")
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Column(nullable = false)
    private String documentUrl;

    @Column(nullable = false)
    private LocalDate expiryDate;

    private Boolean verified = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    @JsonIgnore
    private Driver driver;

    @PrePersist
    public void prePersist() {
        this.uploadedAt = LocalDateTime.now();
    }

    public DriverDocument() {
    }

    public Long getId() {
        return id;
    }

    public DocumentType getType() {
        return type;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Boolean getVerified() {
        return verified;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }
}