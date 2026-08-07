package com.dev.ResQNet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocketSecurity 
public class webSocketConfiguration implements WebSocketMessageBrokerConfigurer{
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/resqnet");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic");
        config.enableSimpleBroker("/queue");
    }

	@Bean
	public AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
		messages
			.simpSubscribeDestMatchers("/topic/**").hasRole("ADMIN")
            .simpSubscribeDestMatchers("/queue/**").hasRole("USER");
		return messages.build();
	}

}
