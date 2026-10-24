package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminDisasterUpdateTest {

    @LocalServerPort
    private int port;

    @Autowired
    private userRepo userRepository;

    @Autowired
    private disasterRepo disasterRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testAdminCanUpdateAllFiveFields() throws Exception {
        // Ensure test admin exists
        userEntity admin = userRepository.findByUsername("test_admin_update");
        if (admin == null) {
            admin = new userEntity();
            admin.setUsername("test_admin_update");
            admin.setPassword(passwordEncoder.encode("adminPass123"));
            admin.setEmail("admin_update@resqnet.com");
            admin.setRoles(List.of("ADMIN"));
            userRepository.save(admin);
        }

        // Create sample disaster
        disasterEntity disaster = new disasterEntity();
        disaster.setUserReport("Initial user report");
        disaster.setSeverity(Severity.LOW);
        disaster.setDisasterType(Set.of(Disaster.FIRE));
        disaster.setForces(Set.of(Forces.FIRE_DEPARTMENT));
        disaster.setSuspicious(false);
        disaster.setStatus(Status.UNDER_REVIEW);
        disaster.setCreatedAt(LocalDateTime.now());
        disasterRepository.save(disaster);
        ObjectId dId = disaster.getDisasterId();

        // Perform login to acquire session
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        // 1. CSRF
        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/public/csrf"))
                .header("X-Requested-With", "XMLHttpRequest")
                .GET().build();
        HttpResponse<String> csrfResp = client.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        String csrfToken = "";
        if (csrfResp.body().contains("\"token\":\"")) {
            csrfToken = csrfResp.body().split("\"token\":\"")[1].split("\"")[0];
        }

        // 2. Login
        String form = "username=test_admin_update&password=adminPass123";
        HttpRequest.Builder loginBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/user/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (!csrfToken.isEmpty()) {
            loginBuilder.header("X-XSRF-TOKEN", csrfToken);
        }
        HttpResponse<String> loginResp = client.send(loginBuilder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResp.statusCode());

        // Refresh CSRF token for the authenticated session
        csrfResp = client.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        if (csrfResp.body().contains("\"token\":\"")) {
            csrfToken = csrfResp.body().split("\"token\":\"")[1].split("\"")[0];
        }

        // 3. Send PATCH /admin/{disasterId}/update with all 5 fields
        String patchJson = """
        {
            "severity": "CRITICAL",
            "forces": ["AMBULANCE", "NDRF", "POLICE_STATION"],
            "disasterType": ["LANDSLIDE", "BUILDING_COLLAPSE"],
            "suspicious": true,
            "userReport": "Updated critical narrative with multiple hazards"
        }
        """;

        HttpRequest.Builder patchBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/" + dId.toHexString() + "/update"))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patchJson));
        if (!csrfToken.isEmpty()) {
            patchBuilder.header("X-XSRF-TOKEN", csrfToken);
        }

        HttpResponse<String> patchResp = client.send(patchBuilder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, patchResp.statusCode(), "PATCH update should return 200 OK: " + patchResp.body());

        // Verify updated entity in MongoDB
        disasterEntity reloaded = disasterRepository.findByDisasterId(dId);
        assertNotNull(reloaded);
        assertEquals(Severity.CRITICAL, reloaded.getSeverity());
        assertTrue(reloaded.getSuspicious());
        assertEquals("Updated critical narrative with multiple hazards", reloaded.getUserReport());
        assertTrue(reloaded.getForces().contains(Forces.AMBULANCE));
        assertTrue(reloaded.getForces().contains(Forces.NDRF));
        assertTrue(reloaded.getForces().contains(Forces.POLICE_STATION));
        assertTrue(reloaded.getDisasterType().contains(Disaster.BUILDING_COLLAPSE));
    }
}
