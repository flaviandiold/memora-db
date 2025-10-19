package com.memora.core;

import com.memora.messages.RpcRequest;
import com.memora.messages.RpcResponse;
import com.memora.services.CommandExecutor;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraServerChannel extends ChannelInitializer<Channel> {

    private final CommandExecutor commandExecutor;

    public MemoraServerChannel(
            CommandExecutor commandExecutor
    ) {
        this.commandExecutor = commandExecutor;
    }

    private class MemoraRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
            RpcResponse response = commandExecutor.execute(request);
            log.info("Sending response: {}", response);
            ctx.writeAndFlush(response);
        }

    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        // === OUTBOUND (Sending RpcResponse) ===
        // These must come first for outbound messages.
        // 1. Prepends the length of the message to the byte stream.
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        // 2. Encodes the RpcResponse object into bytes.
        pipeline.addLast(new ProtobufEncoder());

        // === INBOUND (Receiving RpcRequest) ===
        // 1. Frames the incoming stream by reading the length prefix.
        pipeline.addLast(new ProtobufVarint32FrameDecoder());
        // 2. Decodes the framed bytes into an RpcRequest object.
        pipeline.addLast(new ProtobufDecoder(RpcRequest.getDefaultInstance()));

        // === YOUR BUSINESS LOGIC ===
        // This handler now correctly receives the RpcRequest object.
        pipeline.addLast(new MemoraRequestHandler());
    }

}
