package com.memora.enums;

public enum Operations {
    PUT("PUT"),
    GET("GET"),
    DELETE("DELETE"),
    REPLICATE("REPLICATE"),
    INFO("INFO"),
    UNKNOWN("UNKNOWN");

    private final String operation;

    private Operations(String operation) {
        this.operation = operation;
    }

    public String operation() {
        return operation;
    }
    
}
