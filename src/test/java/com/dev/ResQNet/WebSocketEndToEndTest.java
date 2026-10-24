package com.dev.ResQNet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private userRepo userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testUserDestinationDelivery() throws Exception {
        // Ensure test user exists in MongoDB
        userEntity user = userRepository.findByUsername("ws_test_user");
        if (user == null) {
            user = new userEntity();
            user.setUsername("ws_test_user");
            user.setPassword(passwordEncoder.encode("password123"));
            user.setEmail("ws_test@example.com");
            user.setRoles(List.of("USER"));
            userRepository.save(user);
        }

        // Login via standard HTTP client to obtain JSESSIONID and authenticate session
        CookieManager cookieManager = new CookieManager();
        HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();

        // 1. Fetch CSRF token
        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/public/csrf"))
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build();
        HttpResponse<String> csrfResp = httpClient.send(csrfReq, HttpResponse.BodyHandlers.ofString());

        String csrfToken = "";
        if (csrfResp.body().contains("\"token\":\"")) {
            csrfToken = csrfResp.body().split("\"token\":\"")[1].split("\"")[0];
        }

        // 2. Perform login
        String form = "username=ws_test_user&password=password123";
        HttpRequest.Builder loginBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/user/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (!csrfToken.isEmpty()) {
            loginBuilder.header("X-XSRF-TOKEN", csrfToken);
        }
        HttpResponse<String> loginResp = httpClient.send(loginBuilder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResp.statusCode(), "Login should succeed with 200 OK");

        String jsessionId = null;
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if ("JSESSIONID".equalsIgnoreCase(cookie.getName())) {
                jsessionId = cookie.getValue();
                break;
            }
        }
        assertNotNull(jsessionId, "JSESSIONID must be set upon successful login");

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = "ws://localhost:" + port + "/resqnet";
        StompHeaders connectHeaders = new StompHeaders();

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add("Cookie", "JSESSIONID=" + jsessionId);

        BlockingQueue<reportResponse> queue = new LinkedBlockingQueue<>();

        StompSession session = stompClient.connectAsync(url, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("STOMP Session exception: " + exception.getMessage());
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("Transport error: " + exception.getMessage());
            }
        }).get(5, TimeUnit.SECONDS);

        System.out.println("STOMP Connected: " + session.isConnected() + ", SessionId=" + session.getSessionId());

        session.subscribe("/user/queue/report", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return reportResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("Received frame: " + payload);
                queue.offer((reportResponse) payload);
            }
        });

        Thread.sleep(500);

        // Send to authenticated user destination
        reportResponse sample = new reportResponse("test12345", "Test message", Status.REPORTED);
        messagingTemplate.convertAndSendToUser("ws_test_user", "/queue/report", sample);

        reportResponse received = queue.poll(5, TimeUnit.SECONDS);
        System.out.println("Polled result: " + received);
        assertNotNull(received, "Should receive message on /user/queue/report");
        assertEquals("test12345", received.getDisasterId());

        if (session.isConnected()) {
            session.disconnect();
        }
    }
}
