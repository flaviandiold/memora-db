package com.memora.model;

import java.io.Serializable;
import java.util.Objects;

import lombok.Data;

/**
 * An immutable class representing the state of a node in the cluster, used for gossip.
 * It is uniquely identified by its nodeId.
 */
@Data
public final class NodeInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final String host;
    private final int port;
    private final long epoch;
    private final int heartbeatCounter;
    private final Status status;
    private final long lastUpdateTime;

    public enum Status {
        ALIVE, SUSPECT, FAILED
    }

    public NodeInfo(String nodeId, String host, int port, long epoch, int heartbeatCounter, Status status, long lastUpdateTime) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.port = port;
        this.epoch = epoch;
        this.heartbeatCounter = heartbeatCounter;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.lastUpdateTime = lastUpdateTime;
    }

    public static NodeInfo create(String nodeId, String host, int port) {
        return create(nodeId, host, port, 0, 0, Status.ALIVE, System.currentTimeMillis());
    }

    public static NodeInfo create(String nodeId, String host, int port, long epoch, int heartbeatCounter, Status status, long lastUpdateTime) {
        return new NodeInfo(nodeId, host, port, epoch, heartbeatCounter, status, lastUpdateTime);
    }
}
