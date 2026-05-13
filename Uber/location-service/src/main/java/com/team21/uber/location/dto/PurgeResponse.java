package com.team21.uber.location.dto;

public class PurgeResponse {

    private long deletedCount;

    public PurgeResponse() {
    }

    public PurgeResponse(long deletedCount) {
        this.deletedCount = deletedCount;
    }

    public long getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(long deletedCount) {
        this.deletedCount = deletedCount;
    }
}