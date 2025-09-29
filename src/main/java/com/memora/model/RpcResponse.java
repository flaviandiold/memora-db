package com.memora.model;

import java.io.Serializable;

import com.memora.enums.RpcRespnseStatus;

import lombok.Builder;
import lombok.NonNull;

@Builder
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final RpcResponse OK = withStatus(RpcRespnseStatus.OK).build();
    public static final RpcResponse ERROR = withStatus(RpcRespnseStatus.ERROR).build();
    public static final RpcResponse NOT_FOUND = withStatus(RpcRespnseStatus.NOT_FOUND).build();
    public static final RpcResponse UNSUPPORTED_OPERATION = withStatus(RpcRespnseStatus.UNSUPPORTED_OPERATION).build();
    public static final RpcResponse BAD_REQUEST = withStatus(RpcRespnseStatus.BAD_REQUEST).build();

    @NonNull
    private final RpcRespnseStatus status;
    private String response;

    public RpcResponse(RpcRespnseStatus status, String response) {
        this.status = status;
        this.response = response;
    }

    public RpcResponse(RpcRespnseStatus status) {
        this.status = status;
    }

    public RpcRespnseStatus getStatus() {
        return status;
    }

    public String getResponse() {
        return response;
    }

    private static RpcResponse.RpcResponseBuilder withStatus(RpcRespnseStatus status) {
        return RpcResponse.builder().status(status);
    }

    public static RpcResponse OK(String message) {
        return withStatus(RpcRespnseStatus.OK).response(message).build();
    }

    public static RpcResponse ERROR(String message) {
        return withStatus(RpcRespnseStatus.ERROR).response(message).build();
    }

    public static RpcResponse BAD_REQUEST(String message) {
        return withStatus(RpcRespnseStatus.BAD_REQUEST).response(message).build();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status='").append(status).append('\'');
        if (response != null)
            sb.append(", Response='").append(response).append('\'');
        return sb.toString();
    }

}
