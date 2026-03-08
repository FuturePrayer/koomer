package cn.suhoan.koomer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author sihuangwlp
 * @date 2025/9/12
 */
public class Socks5PasswordAuthRequestHandler extends SimpleChannelInboundHandler<Socks5PasswordAuthRequest> {

    private static final Logger log = LoggerFactory.getLogger(Socks5PasswordAuthRequestHandler.class);

    private final String username;
    private final String password;
    private final LoginAttemptService loginAttemptService;

    public Socks5PasswordAuthRequestHandler(String username, String password) {
        this(username, password, null);
    }

    public Socks5PasswordAuthRequestHandler(String username, String password, LoginAttemptService loginAttemptService) {
        this.username = username;
        this.password = password;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5PasswordAuthRequest msg) {
        String clientIp = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();

        // 检查是否被封禁
        if (loginAttemptService != null && loginAttemptService.isBanned(clientIp)) {
            long remaining = loginAttemptService.getRemainingBanSeconds(clientIp);
            log.warn("Banned IP {} attempted authentication, {} seconds remaining.", clientIp, remaining);
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(future -> ctx.close());
            return;
        }

        if (username.equals(msg.username()) && password.equals(msg.password())) {
            // 认证成功，清除失败记录
            if (loginAttemptService != null) {
                loginAttemptService.recordSuccess(clientIp);
            }
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
            // 移除认证处理器，继续处理命令请求
            ctx.pipeline().remove(this);
        } else {
            // 认证失败，记录失败次数
            if (loginAttemptService != null) {
                loginAttemptService.recordFailure(clientIp);
            }
            log.warn("Authentication failed for IP {}, username: {}", clientIp, msg.username());
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(future -> ctx.close());
        }
    }
}
