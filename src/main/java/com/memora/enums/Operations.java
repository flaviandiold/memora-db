package com.memora.enums;

public enum Operations {
    PUT("PUT"),
    GET("GET"),
    DELETE("DELETE"),
    NODE("NODE"),
    INFO("INFO"),
    UNKNOWN("UNKNOWN");

    private final String operation;
    private final boolean stream;

    private Operations(String operation) {
        this(operation, false);
    }

    private Operations(String operation, boolean stream) {
        this.operation = operation;
        this.stream = stream;
    }

    public String operation() {
        return operation;
    }
    
}
