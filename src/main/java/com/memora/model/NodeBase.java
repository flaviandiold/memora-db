package com.memora.model;

import java.net.InetSocketAddress;

import com.memora.messages.NodeAddress;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class NodeBase {
    private final String host;
    private final int port;

    public NodeBase(InetSocketAddress address) {
        this(address.getAddress().getHostAddress(), address.getPort());
    }

    public boolean equals(String host, int port) {
        return this.host.equals(host) && this.port == port;
    }

    public static NodeBase create(String host, int port) {
        return new NodeBase(host, port);
    }

    public static NodeBase create(NodeAddress address) {
        return new NodeBase(address.getHost(), address.getPort());
    }

}