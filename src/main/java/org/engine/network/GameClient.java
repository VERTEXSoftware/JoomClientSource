package org.engine.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.engine.core.MatchManager;

public class GameClient {


    private EventLoopGroup group;
    private Channel channel;

    // Конструктор, в который передаем менеджер матча из движка
    public GameClient() {

    }

    public void start(String host, int port) {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // ДОБАВЛЕНЫ ЭТИ ДВЕ СТРОКИ для совместимости с сервером
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2));
                            pipeline.addLast(new LengthFieldPrepender(2));

                            // Наши кодеки
                            pipeline.addLast(new PacketDecoder());
                            pipeline.addLast(new PacketEncoder());
                            // Обработчик
                            pipeline.addLast(new ClientNetworkHandler(MatchManager.getInstance()));
                        }
                    });

            System.out.println("Подключение к серверу...");
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();

            channel.closeFuture().sync();
        } catch (Exception e) {
            System.err.println("Ошибка подключения к серверу: " + e.getMessage());
        } finally {
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }

    // Метод для корректного отключения от сервера при закрытии игры
    public void stop() {
        System.out.println("Отключение от сервера...");
        if (channel != null && channel.isActive()) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}