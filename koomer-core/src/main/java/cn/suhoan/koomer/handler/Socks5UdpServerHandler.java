package cn.suhoan.koomer.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author wangzefeng
 * @date 2025/9/12
 */
public class Socks5UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger log = LoggerFactory.getLogger(Socks5UdpServerHandler.class);

    // 用于保存UDP通道引用的AttributeKey
    public static final AttributeKey<Channel> UDP_CHANNEL_KEY = AttributeKey.valueOf("UDP_CHANNEL");

    // 目标已解析地址 → 客户端地址的反向映射（支持多个目标）
    private final ConcurrentMap<InetSocketAddress, InetSocketAddress> targetToClientMap = new ConcurrentHashMap<>();

    private final Channel clientChannel; // 与客户端的TCP连接通道
    private InetSocketAddress clientUdpAddress; // 客户端UDP地址

    public Socks5UdpServerHandler(Channel clientChannel) {
        super(false);
        this.clientChannel = clientChannel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress sender = packet.sender();

        // 检查来源是否是客户端
        if (isClientPacket(sender)) {
            handleClientPacket(ctx, packet);
        } else {
            // 目标服务器返回的数据
            handleTargetResponse(ctx, packet);
        }
    }

    private boolean isClientPacket(InetSocketAddress sender) {
        // 如果已经确定了客户端UDP地址，直接比较
        if (clientUdpAddress != null) {
            return clientUdpAddress.equals(sender);
        }

        // 第一次收到数据包，检查IP是否与TCP连接的客户端IP一致
        if (clientChannel.isActive()) {
            InetSocketAddress tcpClientAddr = (InetSocketAddress) clientChannel.remoteAddress();
            if (tcpClientAddr != null && sender.getAddress().equals(tcpClientAddr.getAddress())) {
                // 锁定客户端UDP地址
                clientUdpAddress = sender;
                return true;
            }
        }

        return false;
    }


    private void handleClientPacket(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress clientAddress = packet.sender();
        ByteBuf buf = packet.content();

        try {
            // 解析SOCKS5 UDP头部
            // 跳过RSV（2字节）和FRAG（1字节）
            buf.skipBytes(3);

            // 读取地址类型
            byte addrType = buf.readByte();
            String dstAddr;
            int dstPort;

            // 根据地址类型读取目标地址
            if (addrType == Socks5AddressType.IPv4.byteValue()) {
                byte[] addrBytes = new byte[4];
                buf.readBytes(addrBytes);
                dstAddr = NetUtil.bytesToIpAddress(addrBytes);
            } else if (addrType == Socks5AddressType.DOMAIN.byteValue()) {
                int domainLength = buf.readByte() & 0xFF;
                byte[] domainBytes = new byte[domainLength];
                buf.readBytes(domainBytes);
                dstAddr = new String(domainBytes);
            } else if (addrType == Socks5AddressType.IPv6.byteValue()) {
                byte[] addrBytes = new byte[16];
                buf.readBytes(addrBytes);
                dstAddr = NetUtil.bytesToIpAddress(addrBytes);
            } else {
                // 未知地址类型，丢弃数据包
                return;
            }

            // 读取目标端口
            dstPort = buf.readUnsignedShort();

            // 剩余数据是实际要转发的UDP数据
            ByteBuf data = buf.readBytes(buf.readableBytes());

            // 创建目标地址（构造函数会触发DNS解析，解析后的IP可用于匹配响应）
            InetSocketAddress targetAddress = new InetSocketAddress(dstAddr, dstPort);

            // 保存已解析的目标地址 → 客户端地址的反向映射
            // 使用已解析的IP地址作为key，这样当目标服务器响应时可以正确匹配
            InetSocketAddress resolvedTarget = new InetSocketAddress(targetAddress.getAddress(), dstPort);
            targetToClientMap.put(resolvedTarget, clientAddress);

            // 转发数据到目标服务器
            ctx.writeAndFlush(new DatagramPacket(data, targetAddress)).addListener(future -> {
                if (!future.isSuccess()) {
                    log.warn("Failed to forward UDP packet to {}:{}", dstAddr, dstPort, future.cause());
                    targetToClientMap.remove(resolvedTarget);
                }
            });
        } finally {
            // 释放原始DatagramPacket（autoRelease=false，需要手动释放）
            packet.release();
        }
    }

    private void handleTargetResponse(ChannelHandlerContext ctx, DatagramPacket packet) {
        try {
            InetSocketAddress targetAddress = packet.sender();

            // 通过已解析的目标地址查找对应的客户端地址
            InetSocketAddress clientAddress = targetToClientMap.get(targetAddress);

            if (clientAddress == null) {
                // 没有找到对应的客户端，丢弃数据包
                log.debug("No client mapping found for target {}", targetAddress);
                return;
            }

            // 获取响应数据
            ByteBuf data = packet.content();

            // 构造SOCKS5 UDP响应头部
            ByteBuf responseBuf = ctx.alloc().buffer();
            // RSV: 2字节0
            responseBuf.writeShort(0);
            // FRAG: 1字节0
            responseBuf.writeByte(0);
            // ATYP: 根据目标地址类型
            byte[] addrBytes = targetAddress.getAddress().getAddress();
            if (addrBytes.length == 4) {
                responseBuf.writeByte(Socks5AddressType.IPv4.byteValue());
                responseBuf.writeBytes(addrBytes);
            } else if (addrBytes.length == 16) {
                responseBuf.writeByte(Socks5AddressType.IPv6.byteValue());
                responseBuf.writeBytes(addrBytes);
            } else {
                // 不支持的地址类型，丢弃
                responseBuf.release();
                return;
            }
            // 端口
            responseBuf.writeShort(targetAddress.getPort());
            // 响应数据
            responseBuf.writeBytes(data);

            // 发送给客户端（Netty会在writeAndFlush完成后自动释放DatagramPacket及其content）
            ctx.writeAndFlush(new DatagramPacket(responseBuf, clientAddress)).addListener(future -> {
                if (!future.isSuccess()) {
                    log.warn("Failed to send UDP response to client {}", clientAddress, future.cause());
                }
            });
        } finally {
            // 释放原始DatagramPacket（autoRelease=false，需要手动释放）
            packet.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Udp server error", cause);
        ctx.close();
    }
}
