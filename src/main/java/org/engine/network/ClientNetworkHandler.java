package org.engine.network;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.engine.core.MatchManager;

public class ClientNetworkHandler extends SimpleChannelInboundHandler<Object> {

    private final MatchManager matchManager;

    public ClientNetworkHandler(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("Соединение с сервером установлено!");

        // 1. Передаем контекст в MatchManager
        matchManager.setNetworkContext(ctx);

        // 2. Теперь, когда контекст есть, отправляем логин
       // matchManager.SendLogin("Player_" + (int)(Math.random() * 1000));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        matchManager.addIncomingPacket(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}