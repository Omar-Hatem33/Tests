package com.team21.uber.driver.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "drivers", createIndex = false)
public class DriverSearchDocument {

    @Id
    private String id;           // stores the PG driver id as a string (ES ids are strings)

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String vehicleType;

    @Field(type = FieldType.Double)
    private Double rating;

    public DriverSearchDocument() {}

    public DriverSearchDocument(String id, String name, String description,
                                String status, String vehicleType, Double rating) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.vehicleType = vehicleType;
        this.rating = rating;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
}
