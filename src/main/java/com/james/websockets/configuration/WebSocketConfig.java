package com.james.websockets.configuration;

import com.james.websockets.handler.SocketConnectionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final SocketConnectionHandler socketConnectionHandler;

    public WebSocketConfig(SocketConnectionHandler socketConnectionHandler) {
        this.socketConnectionHandler = socketConnectionHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socketConnectionHandler, "/hello")
                // Restrict this to your deployed domain for production.
                .setAllowedOriginPatterns("https://echo-chatroom-n9ds.onrender.com/");
    }
}
