package com.team21.uber.ride.adapter;

import java.util.Map;

public class Neo4jRecordAdapter {

    public Map<String, Object> adapt(org.neo4j.driver.Record record) {
        Map<String, Object> result = new java.util.HashMap<>();
        record.keys().forEach(key -> result.put(key, record.get(key).asObject()));
        return result;
    }
}
