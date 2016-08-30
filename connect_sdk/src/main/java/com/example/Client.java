package com.example;

import java.util.Scanner;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

/**
 * Created by llx on 2016/8/28.
 */
public class Client {
    public void connect(int port, String host) {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();

            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            //解决半包和粘包问题
                            ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                            ch.pipeline().addLast(new ProtobufDecoder(ImMessagePojo.IMMessage.getDefaultInstance()));
                            ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                            ch.pipeline().addLast(new ProtobufEncoder());
                            ch.pipeline().addLast(new TimeClientHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            System.out.println("connect port = [" + port + "], host = [" + host + "]");

            future.channel().closeFuture().sync();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    private class TimeClientHandler extends ChannelInboundHandlerAdapter {

        private ChannelHandlerContext mCtx;

        private int counter = 0;

        public TimeClientHandler() {
        }

        /**
         * 通道会消息回调
         *
         * @param ctx
         * @param msg
         * @throws Exception
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ImMessagePojo.IMMessage imMessage = (ImMessagePojo.IMMessage) msg;
            String recStr = imMessage.getBody();
            System.out.println("rec:" + recStr);
        }

        /**
         * 通道建立会调用
         *
         * @param ctx
         * @throws Exception
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            mCtx = ctx;
            new Thread() {
                @Override
                public void run() {
                    while (true) {
                        Scanner s = new Scanner(System.in);
                        String getIn = s.nextLine();
                        ImMessagePojo.IMMessage.Builder builder = ImMessagePojo.IMMessage.newBuilder();
                        builder.setId(0);
                        builder.setCmd("Message");
                        builder.setBody(getIn);
                        sendMsg(builder.build());
                    }
                }
            }.start();
        }

        private void sendMsg(ImMessagePojo.IMMessage build) {
            mCtx.writeAndFlush(build);
        }

        public void sendMsg(String i) {
            mCtx.writeAndFlush(Unpooled.copiedBuffer(i.getBytes()));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("exceptionCaught ctx = [" + ctx + "], cause = [" + cause + "]");
            ctx.close();
        }
    }

    public static void main(String[] args) {
        new Client().connect(8889, "127.0.0.1");
    }
}
