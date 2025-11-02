package com.memora.core;

import com.google.inject.Inject;
import com.memora.messages.RpcResponse;
import com.memora.services.ClientManager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MemoraClientChannel extends ChannelInitializer<Channel> {

    private final ClientManager clientManager;

    private class ClientResponseHandler extends SimpleChannelInboundHandler<RpcResponse> {

        public ClientResponseHandler() {
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
            // 1. Get the request ID from the response
            String correlationId = response.getCorrelationId();

            if (correlationId == null || correlationId.isEmpty()) {
                log.warn("Received response without correlation ID: {}", response);
                return;
            }
            
            // 2. Find the corresponding future from the map and remove it
            clientManager.resolveRequest(correlationId, response);
        }
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        
        // OUTBOUND (Sending RpcRequest)
        p.addLast(new ProtobufVarint32LengthFieldPrepender());
        p.addLast(new ProtobufEncoder());

        // INBOUND (Receiving RpcResponse)
        p.addLast(new ProtobufVarint32FrameDecoder());
        p.addLast(new ProtobufDecoder(RpcResponse.getDefaultInstance()));
        
        // The handler that matches responses to requests
        p.addLast(new ClientResponseHandler());
    }
}
