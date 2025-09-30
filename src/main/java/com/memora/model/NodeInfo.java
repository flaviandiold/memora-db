package com.memora.model;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Objects;

import com.memora.enums.NodeType;

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
    
    private NodeType nodeType;
    private Status status;
    private long epoch;
    private int heartbeatCounter;
    private long lastUpdateTime;
    

    public enum Status {
        ALIVE, SUSPECT, FAILED
    }

    public NodeInfo(String nodeId, String host, int port, long epoch, int heartbeatCounter, NodeType nodeType, Status status, long lastUpdateTime) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) throw new RuntimeException("Invalid address: " + address);
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.host = address.getAddress().getHostAddress();
        this.port = address.getPort();
        this.epoch = epoch;
        this.heartbeatCounter = heartbeatCounter;
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.lastUpdateTime = lastUpdateTime;
    }

    public static NodeInfo create(String nodeId, String host, int port) {
        return create(nodeId, host, port, 0, 0, Status.ALIVE, System.currentTimeMillis());
    }

    public static NodeInfo create(String nodeId, String host, int port, long epoch, int heartbeatCounter, Status status, long lastUpdateTime) {
        return new NodeInfo(nodeId, host, port, epoch, heartbeatCounter, NodeType.STANDALONE, status, lastUpdateTime);
    }

    public boolean equals(String host, int port) {
        return this.host.equals(host) && this.port == port;
    }
}
