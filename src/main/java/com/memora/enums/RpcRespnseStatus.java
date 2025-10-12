package com.memora.enums;

public enum RpcRespnseStatus {
    OK("OK"),
    PARTIAL_FULFILLMENT("PARTIAL_FULFILLMENT"),
    ERROR("ERROR"),
    NOT_FOUND("NOT_FOUND"),
    UNSUPPORTED_OPERATION("UNSUPPORTED_OPERATION"),
    BAD_REQUEST("BAD_REQUEST");

    private final String status;

    private RpcRespnseStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
