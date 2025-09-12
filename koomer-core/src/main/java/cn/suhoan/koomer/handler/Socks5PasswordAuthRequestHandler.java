package cn.suhoan.koomer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;

/**
 * @author sihuangwlp
 * @date 2025/9/12
 */
public class Socks5PasswordAuthRequestHandler extends SimpleChannelInboundHandler<Socks5PasswordAuthRequest> {

    private final String username;
    private final String password;

    public Socks5PasswordAuthRequestHandler(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5PasswordAuthRequest msg) {
        if (username.equals(msg.username()) && password.equals(msg.password())) {
            // 认证成功
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
            // 移除认证处理器，继续处理命令请求
            ctx.pipeline().remove(this);
        } else {
            // 认证失败
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(future -> ctx.close());
        }
    }
}
