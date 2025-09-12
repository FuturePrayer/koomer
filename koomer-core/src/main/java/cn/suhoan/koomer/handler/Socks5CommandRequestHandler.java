package cn.suhoan.koomer.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private static final Logger log = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        if (request.type() == Socks5CommandType.CONNECT) {
            handleConnect(ctx, request);
        } else if (request.type() == Socks5CommandType.UDP_ASSOCIATE) {
            handleUdpAssociate(ctx, request);
        } else {
            // 不支持其他命令
            sendErrorResponse(ctx, request, Socks5CommandStatus.COMMAND_UNSUPPORTED);
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        // 解析目标地址
        String dstAddr = request.dstAddr();
        int dstPort = request.dstPort();
        log.info("Connect to {}:{}", dstAddr, dstPort);

        // 创建Bootstrap连接目标服务器
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                })
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        b.connect(dstAddr, dstPort).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                // 连接成功
                Channel outboundChannel = future.channel();
                Channel inboundChannel = ctx.channel();

                // 发送成功响应
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                                request.dstAddrType(), request.dstAddr(), request.dstPort()))
                        .addListener((ChannelFutureListener) f -> {
                            if (!f.isSuccess()) {
                                // 响应发送失败
                                outboundChannel.close();
                                ctx.close();
                            }
                        });

                // 设置转发
                inboundChannel.pipeline().addLast(new RelayHandler(outboundChannel));
            } else {
                // 连接失败
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE,
                                request.dstAddrType(), request.dstAddr(), request.dstPort()))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    private void handleUdpAssociate(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        // 创建UDP服务器
        EventLoopGroup udpGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(udpGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new Socks5UdpServerHandler(ctx.channel()));
                    }
                });

        // 绑定到随机端口
        ChannelFuture bindFuture = udpBootstrap.bind(0);

        bindFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 获取UDP服务器地址
                InetSocketAddress udpServerAddress = (InetSocketAddress) future.channel().localAddress();
                log.info("UDP server started at {}:{}, bind with client {}:{}", udpServerAddress.getAddress(), udpServerAddress.getPort(), request.dstAddr(), request.dstPort());

                // 发送成功响应，包含UDP服务器地址
                String hostAddress = udpServerAddress.getAddress().getHostAddress();
                Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS,
                        hostAddress.contains(":") ? Socks5AddressType.IPv6 : Socks5AddressType.IPv4,
                        hostAddress,
                        udpServerAddress.getPort()
                );

                ctx.writeAndFlush(response).addListener(f -> {
                    if (!f.isSuccess()) {
                        future.channel().close();
                        udpGroup.shutdownGracefully();
                        ctx.close();
                    }
                });

                // 保存UDP通道引用，以便在TCP连接关闭时关闭UDP服务器
                ctx.channel().attr(Socks5UdpServerHandler.UDP_CHANNEL_KEY).set(future.channel());
            } else {
                sendErrorResponse(ctx, request, Socks5CommandStatus.FAILURE);
                udpGroup.shutdownGracefully();
            }
        });
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, Socks5CommandRequest request, Socks5CommandStatus status) {
        Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                status,
                request.dstAddrType(),
                request.dstAddr(),
                request.dstPort()
        );
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 当TCP连接关闭时，关闭关联的UDP服务器
        Channel udpChannel = ctx.channel().attr(Socks5UdpServerHandler.UDP_CHANNEL_KEY).get();
        if (udpChannel != null) {
            udpChannel.close();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Socks5 command handle error", cause);
        ctx.close();
    }
}