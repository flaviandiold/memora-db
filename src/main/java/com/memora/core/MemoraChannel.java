package com.memora.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.memora.model.RpcRequest;
import com.memora.model.RpcResponse;
import com.memora.services.CommandExecutor;
import com.memora.utils.Parser;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoraChannel extends ChannelInitializer {

    private final Version version;
    private final CommandExecutor commandExecutor;

    private static final Charset CHARSET = StandardCharsets.UTF_8;


    public MemoraChannel(
            Version version,
            CommandExecutor commandExecutor
    ) {
        this.version = version;
        this.commandExecutor = commandExecutor;
    }

    private class MemoraRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
            RpcResponse response = commandExecutor.execute(request);
            ctx.writeAndFlush(response);
        }

    }

    private class MemoraRequestSerializer extends MessageToByteEncoder<RpcResponse> {

        @Override
        protected void encode(ChannelHandlerContext ctx, RpcResponse msg, ByteBuf out) {
            StringBuilder builder = new StringBuilder();
            builder.append(Parser.toJson(msg)).append('\n');
            byte[] bytes = builder.toString().getBytes(CHARSET);
            out.writeBytes(bytes);
        }
    }

    private class MemoraRequestDeserializer extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            byte[] bytes = new byte[in.readableBytes()];
            in.readBytes(bytes);

            String command = new String(bytes, CHARSET).trim();
            int idx = command.indexOf(' ');
            String operation = idx != -1 ? command.substring(0, idx) : command;

            RpcRequest request = RpcRequest.builder()
                    .operation(operation)
                    .version(version.get())
                    .command(command)
                    .build();

            out.add(request);
        }

    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        // Binary decoder and encoder
        pipeline.addLast(new MemoraRequestDeserializer());
        pipeline.addLast(new MemoraRequestSerializer());

        // Handles decoded requests
        pipeline.addLast(new MemoraRequestHandler());
    }

}
