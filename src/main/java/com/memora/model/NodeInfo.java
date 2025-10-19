package com.memora.model;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Objects;

import com.memora.enums.NodeState;
import com.memora.enums.NodeType;
import com.memora.exceptions.MemoraException;
import com.memora.utils.QPS;

import lombok.Data;

/**
 * An immutable class representing the state of a node in the cluster, used for gossip.
 * It is uniquely identified by its nodeId.
 */
@Data
public final class NodeInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final NodeBase nodeBase;

    private NodeType type;
    private NodeState status;
    private long epoch;
    private int heartbeatCounter;
    private long lastUpdateTime;
    
    public NodeInfo(String nodeId, String host, int port, long epoch, int heartbeatCounter, NodeType type, NodeState status, long lastUpdateTime) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) throw new MemoraException("Invalid address: " + address);
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.nodeBase = new NodeBase(address);
        this.epoch = epoch;
        this.heartbeatCounter = heartbeatCounter;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.lastUpdateTime = lastUpdateTime;
    }

    public static NodeInfo create(String nodeId, NodeBase base) {
        return create(nodeId, base.getHost(), base.getPort(), NodeType.STANDALONE);
    }

    public static NodeInfo create(String nodeId, String host, int port) {
        return create(nodeId, host, port, NodeType.STANDALONE);
    }

    public static NodeInfo create(String nodeId, String host, int port, NodeType type) {
        return create(nodeId, host, port, type, 0, 0, NodeState.ALIVE, System.currentTimeMillis());
    }

    public static NodeInfo create(String nodeId, String host, int port, NodeType type, long epoch, int heartbeatCounter, NodeState status, long lastUpdateTime) {
        return new NodeInfo(nodeId, host, port, epoch, heartbeatCounter, type, status, lastUpdateTime);
    }

    public boolean equals(String host, int port) {
        return this.nodeBase.equals(host, port);
    }

    public boolean isPrimary() {
        return NodeType.PRIMARY.equals(type);
    }

    public boolean isReplica() {
        return NodeType.REPLICA.equals(type);
    }

    public String getHost() {
        return nodeBase.getHost();
    }

    public int getPort() {
        return nodeBase.getPort();
    }

    public int getMaxQps() {
        return QPS.getInstance().getMax();
    }

    public int getCurrentQps() {
        return QPS.getInstance().get();
    }
}
