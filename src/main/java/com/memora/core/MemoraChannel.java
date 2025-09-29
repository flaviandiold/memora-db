package com.memora.core;

import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.CommandExecutor;
import com.memora.utils.Parser;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraChannel extends ChannelInitializer {

    private final Version version;
    private final CommandExecutor commandExecutor;

    public MemoraChannel(
            Version version,
            CommandExecutor commandExecutor
    ) {
        this.version = version;
        this.commandExecutor = commandExecutor;
    }

    private class MemoraRequestHandler extends SimpleChannelInboundHandler<String> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String request) throws Exception {
            request = request.trim();
            log.info("Received request: '{}'", request);
            int idx = request.indexOf(' ');
            if (idx == -1) {
                ctx.writeAndFlush(RpcResponse.BAD_REQUEST.toString());
                return;
            }
            String operation = request.substring(0, idx);
            RpcRequest rpcRequest = RpcRequest.builder()
                    .operation(operation)
                    .version(version.get())
                    .command(request)
                    .build();
            RpcResponse response = commandExecutor.execute(rpcRequest);
            ctx.writeAndFlush(Parser.toJson(response) + '\n');
        }

    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new StringDecoder());
        pipeline.addLast(new StringEncoder());
        pipeline.addLast(new MemoraRequestHandler());
    }

}
