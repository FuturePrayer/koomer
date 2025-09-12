package cn.suhoan.koomer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<Socks5InitialRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5InitialRequest msg) {
        if (msg.authMethods().contains(Socks5AuthMethod.NO_AUTH)) {
            // 支持无认证
            Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
            ctx.writeAndFlush(response);
        } else {
            // 不支持其他认证方式
            Socks5InitialResponse response = new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED);
            ctx.writeAndFlush(response).addListener(future -> ctx.close());
        }
    }
}