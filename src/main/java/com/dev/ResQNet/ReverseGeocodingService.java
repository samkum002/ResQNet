package com.dev.ResQNet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

@Service
public class ReverseGeocodingService {

    @Autowired
    private disasterRepo disasterRepository;

    private final WebClient webClient = WebClient.create("https://nominatim.openstreetmap.org");

    public void findAndSaveState(
            disasterEntity disaster,
            double latitude,
            double longitude) {

        String state = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/reverse")
                        .queryParam("lat", latitude)
                        .queryParam("lon", longitude)
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", 1)
                        .build())
                .header(HttpHeaders.USER_AGENT, "ResQNet/1.0")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json
                        .path("address")
                        .path("state")
                        .asText())
                .block();

        disaster.setState(state);
        disasterRepository.save(disaster);

    }
}
