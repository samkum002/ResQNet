package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class OsrmRouteServiceTest {

    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec uriSpec;
    private WebClient.RequestHeadersSpec headersSpec;
    private WebClient.ResponseSpec responseSpec;
    private ObjectMapper objectMapper;
    private OsrmRouteService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        webClient = mock(WebClient.class);
        uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        headersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
        objectMapper = new ObjectMapper();

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(URI.class))).thenReturn(headersSpec);
        when(headersSpec.header(any(), any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        service = new OsrmRouteService(webClient, objectMapper);
    }

    @Test
    void successfullyDeserializesValidOsrmResponseWithoutJsonNodeError() {
        String validOsrmJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "geometry": {
                    "coordinates": [
                      [77.2090, 28.6139],
                      [77.2095, 28.6145],
                      [77.2100, 28.6150]
                    ],
                    "type": "LineString"
                  },
                  "duration": 120.5,
                  "distance": 850.2
                }
              ],
              "waypoints": []
            }
            """;

        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(validOsrmJson));

        List<List<Double>> route = service.getRoute(28.6139, 77.2090, 28.6150, 77.2100);

        assertNotNull(route);
        assertEquals(3, route.size());
        assertEquals(77.2090, route.get(0).get(0));
        assertEquals(28.6139, route.get(0).get(1));
        assertEquals(77.2100, route.get(2).get(0));
        assertEquals(28.6150, route.get(2).get(1));
    }

    @Test
    void handlesNoRouteResponseFromOsrmCleanly() {
        String noRouteJson = """
            {
              "code": "NoRoute",
              "message": "Impossible route between points"
            }
            """;

        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(noRouteJson));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.getRoute(28.6139, 77.2090, 28.6150, 77.2100));

        assertTrue(ex.getMessage().contains("NoRoute"));
        assertTrue(ex.getMessage().contains("Impossible route"));
    }

    @Test
    void handlesEmptyOrInvalidResponseBodyCleanly() {
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("   "));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.getRoute(28.6139, 77.2090, 28.6150, 77.2100));

        assertTrue(ex.getMessage().contains("empty response"));
    }
}
