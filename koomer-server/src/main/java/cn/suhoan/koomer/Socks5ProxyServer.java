package cn.suhoan.koomer;

import cn.suhoan.koomer.handler.Socks5CommandRequestHandler;
import cn.suhoan.koomer.handler.Socks5InitialRequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class Socks5ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyServer.class);

    private final String host;

    private final int port;
    private final boolean enableAuth;
    private final String username;
    private final String password;

    public Socks5ProxyServer(String host, int port) {
        this(host, port, false, null, null);
    }

    public Socks5ProxyServer(String host, int port, boolean enableAuth, String username, String password) {
        this.port = port;
        this.host = host;
        this.enableAuth = enableAuth;
        this.username = username;
        this.password = password;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(r -> {
            return Thread.ofVirtual().unstarted(r);
        }, NioIoHandler.newFactory());

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(Socks5ServerEncoder.DEFAULT)
                                    .addLast(new Socks5InitialRequestDecoder());
                            if (enableAuth) {
                                ch.pipeline()
                                        .addLast(new Socks5InitialRequestHandler(enableAuth, username, password))
                                        .addLast(new Socks5PasswordAuthRequestDecoder());
                            } else {
                                ch.pipeline()
                                        .addLast(new Socks5InitialRequestHandler());
                            }
                            ch.pipeline()
                                    .addLast(new Socks5CommandRequestDecoder())
                                    .addLast(new Socks5CommandRequestHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            log.info("SOCKS5 Proxy Server started on {}:{} with the authentication mode is {}.", host, port, enableAuth ? "enabled" : "disabled");
            ChannelFuture f = b.bind(host, port).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            log.info("SOCKS5 Proxy Server stopped.");
        }
    }
}