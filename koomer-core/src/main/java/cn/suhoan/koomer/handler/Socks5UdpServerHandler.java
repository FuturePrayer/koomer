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

    // 客户端地址到目标地址的映射
    private final ConcurrentMap<InetSocketAddress, InetSocketAddress> clientToTargetMap = new ConcurrentHashMap<>();

    private final Channel clientChannel; // 与客户端的TCP连接通道

    public Socks5UdpServerHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress sender = packet.sender();
        ByteBuf buf = packet.content();

        // 检查是否是客户端发送的数据（带有SOCKS5头部）
        if (isSocks5UdpPacket(buf)) {
            handleClientPacket(ctx, packet);
        } else {
            // 目标服务器返回的数据
            handleTargetResponse(ctx, packet);
        }
    }

    private boolean isSocks5UdpPacket(ByteBuf buf) {
        if (buf.readableBytes() < 4) {
            return false;
        }

        // 保存当前读取位置
        buf.markReaderIndex();

        // 读取RSV（2字节）和FRAG（1字节）
        short rsv = buf.readShort();
        byte frag = buf.readByte();

        // 恢复读取位置
        buf.resetReaderIndex();

        // 检查RSV是否为0，FRAG是否为0（不支持分片）
        return rsv == 0 && frag == 0;
    }

    private void handleClientPacket(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress clientAddress = packet.sender();
        ByteBuf buf = packet.content();

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

        // 创建目标地址
        InetSocketAddress targetAddress = new InetSocketAddress(dstAddr, dstPort);

        // 保存客户端到目标的映射
        clientToTargetMap.put(clientAddress, targetAddress);

        // 转发数据到目标服务器
        ctx.writeAndFlush(new DatagramPacket(data, targetAddress)).addListener(future -> {
            if (!future.isSuccess()) {
                data.release();
                clientToTargetMap.remove(clientAddress);
            }
        });
    }

    private void handleTargetResponse(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress targetAddress = packet.sender();

        // 查找对应的客户端地址
        InetSocketAddress clientAddress = null;
        for (ConcurrentMap.Entry<InetSocketAddress, InetSocketAddress> entry : clientToTargetMap.entrySet()) {
            if (entry.getValue().equals(targetAddress)) {
                clientAddress = entry.getKey();
                break;
            }
        }

        if (clientAddress == null) {
            // 没有找到对应的客户端，丢弃数据包
            packet.content().release();
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
            data.release();
            responseBuf.release();
            return;
        }
        // 端口
        responseBuf.writeShort(targetAddress.getPort());
        // 响应数据
        responseBuf.writeBytes(data);

        // 发送给客户端
        ctx.writeAndFlush(new DatagramPacket(responseBuf, clientAddress)).addListener(future -> {
            if (!future.isSuccess()) {
                responseBuf.release();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Udp server error", cause);
        ctx.close();
    }
}
