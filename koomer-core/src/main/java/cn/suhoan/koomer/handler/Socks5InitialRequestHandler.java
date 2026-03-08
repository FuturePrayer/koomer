package cn.suhoan.koomer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<Socks5InitialRequest> {

    private static final Logger log = LoggerFactory.getLogger(Socks5InitialRequestHandler.class);

    private final boolean enableAuth;
    private final String username;
    private final String password;
    private final LoginAttemptService loginAttemptService;

    public Socks5InitialRequestHandler() {
        this(false, null, null, null);
    }

    public Socks5InitialRequestHandler(boolean enableAuth, String username, String password) {
        this(enableAuth, username, password, null);
    }

    public Socks5InitialRequestHandler(boolean enableAuth, String username, String password, LoginAttemptService loginAttemptService) {
        this.enableAuth = enableAuth;
        this.username = username;
        this.password = password;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5InitialRequest msg) {
        // 如果开启了认证且启用了封禁功能，先检查IP是否被封禁
        if (enableAuth && loginAttemptService != null) {
            String clientIp = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
            if (loginAttemptService.isBanned(clientIp)) {
                long remaining = loginAttemptService.getRemainingBanSeconds(clientIp);
                log.warn("Banned IP {} attempted to connect, {} seconds remaining.", clientIp, remaining);
                Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED);
                ctx.writeAndFlush(response).addListener(future -> ctx.close());
                return;
            }
        }

        if (!enableAuth && msg.authMethods().contains(Socks5AuthMethod.NO_AUTH)) {
            // 支持无认证
            Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
            ctx.writeAndFlush(response);
        } else if (enableAuth && msg.authMethods().contains(Socks5AuthMethod.PASSWORD)) {
            // 支持密码认证
            Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD);
            ctx.writeAndFlush(response);
            // 添加密码认证处理器
            ctx.pipeline().addLast(new Socks5PasswordAuthRequestHandler(username, password, loginAttemptService));
        } else {
            // 不支持其他认证方式
            Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED);
            ctx.writeAndFlush(response).addListener(future -> ctx.close());
        }
    }
}