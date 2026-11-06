package com.swd392.aiviva.speech.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import com.swd392.aiviva.speech.config.SpeechToTextProperties;

@Configuration
@EnableWebSocket
public class SttWebSocketConfig implements WebSocketConfigurer {

    private final SttWebSocketHandler sttWebSocketHandler;
    private final SttHandshakeInterceptor handshakeInterceptor;
    private final SpeechToTextProperties properties;

    public SttWebSocketConfig(
            SttWebSocketHandler sttWebSocketHandler,
            SttHandshakeInterceptor handshakeInterceptor,
            SpeechToTextProperties properties) {
        this.sttWebSocketHandler = sttWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketHandlerRegistration registration = registry.addHandler(sttWebSocketHandler, "/ws/stt")
                .addInterceptors(handshakeInterceptor);
        String allowedOrigins = properties.getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            registration.setAllowedOrigins(allowedOrigins.split(","));
        }
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8 * 1024);
        container.setMaxBinaryMessageBufferSize(32 * 1024);
        return container;
    }
}
