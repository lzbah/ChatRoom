package com.example;

import java.util.Scanner;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

public class Server {

    public void bind(int port) {
        //两个工作线程组，实际上是Reactor线程组
        EventLoopGroup boosGroup = new NioEventLoopGroup(); //服务端接收客户端的连接
        EventLoopGroup workerGroup = new NioEventLoopGroup();   //进行SocketChannel的网络读写

        try {

            ServerBootstrap bootstrap = new ServerBootstrap();  //用于启动服务端的辅助启动类，目的降低服务端的开发复杂度
            bootstrap.group(boosGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ServerChannelHandler());   //用于处理网络I/O事件

            //绑定端口，同步等待成功
            ChannelFuture channelFuture = bootstrap.bind(port).sync();
            System.out.println("listener port:" + port);
            //等待服务端监听端口关闭
            channelFuture.channel().closeFuture().sync();
            System.out.println("listener close:");
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            boosGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    private class ServerChannelHandler extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
            ch.pipeline().addLast(new ProtobufDecoder(ImMessagePojo.IMMessage.getDefaultInstance()));
            ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
            ch.pipeline().addLast(new ProtobufEncoder());
            ch.pipeline().addLast(new ServerMessageHandler());
        }
    }


    private class ServerMessageHandler extends ChannelInboundHandlerAdapter {
        private ChannelHandlerContext mCtx;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ImMessagePojo.IMMessage imMessage = (ImMessagePojo.IMMessage) msg;
            String recStr = imMessage.getBody();
            System.out.println("rec:" + recStr);
        }

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

        }
    }

    public static void main(String[] args) {
        int port = 8889;
        new Server().bind(port);
    }
}
