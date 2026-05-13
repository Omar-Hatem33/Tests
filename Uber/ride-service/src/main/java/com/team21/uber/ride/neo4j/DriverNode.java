package com.team21.uber.ride.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Driver")
public class DriverNode {

    @Id
    private Long driverId;

    private String name;

    private String vehicleType;

    public DriverNode() {}

    public DriverNode(Long driverId, String name, String vehicleType) {
        this.driverId = driverId;
        this.name = name;
        this.vehicleType = vehicleType;
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
}
