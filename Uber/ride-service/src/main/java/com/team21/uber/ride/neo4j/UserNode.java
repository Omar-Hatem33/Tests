package com.team21.uber.ride.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node("User")
public class UserNode {

    @Id
    private Long userId;

    private String name;

    @Relationship(type = "RODE_WITH", direction = Relationship.Direction.OUTGOING)
    private List<RodeWithRelationship> rodeWithDrivers = new ArrayList<>();

    public UserNode() {}

    public UserNode(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<RodeWithRelationship> getRodeWithDrivers() { return rodeWithDrivers; }
    public void setRodeWithDrivers(List<RodeWithRelationship> rodeWithDrivers) { this.rodeWithDrivers = rodeWithDrivers; }
}
