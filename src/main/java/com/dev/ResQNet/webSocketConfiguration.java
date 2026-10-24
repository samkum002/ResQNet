package com.dev.ResQNet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocketSecurity 
public class webSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/resqnet").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/update", "/new");
        config.setUserDestinationPrefix("/user");
    }

    @Bean("csrfChannelInterceptor")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {};
    }

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
            .nullDestMatcher().permitAll()
            .simpSubscribeDestMatchers("/user/queue/**", "/queue/**").hasRole("USER")
            .simpSubscribeDestMatchers("/user/topic/**", "/topic/**").hasRole("ADMIN")
            .simpSubscribeDestMatchers("/user/update/**", "/update/**").hasRole("STATION_MANAGER")
            .simpSubscribeDestMatchers("/user/new/**", "/new/**").hasRole("WORKER")
            .anyMessage().permitAll();
        return messages.build();
    }

}
