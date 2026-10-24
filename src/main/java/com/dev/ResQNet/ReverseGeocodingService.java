package com.dev.ResQNet;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;

@Service
public class ReverseGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(ReverseGeocodingService.class);

    @Autowired
    private disasterRepo disasterRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final WebClient webClient = WebClient.create("https://nominatim.openstreetmap.org");

    public void findAndSaveState(
            disasterEntity disaster,
            double latitude,
            double longitude) {
        
        log.info("Reverse geocoding for coordinates: [{}, {}]", latitude, longitude);

        String state = null;
        try {
            state = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .build())
                    .header(HttpHeaders.USER_AGENT, "ResQNet/1.0 (Emergency Response System)")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(json -> {
                        JsonNode address = json.path("address");
                        if (address.has("state") && !address.path("state").asString().isBlank()) {
                            return address.path("state").asString();
                        }
                        if (address.has("state_district") && !address.path("state_district").asString().isBlank()) {
                            return address.path("state_district").asString();
                        }
                        if (address.has("city") && !address.path("city").asString().isBlank()) {
                            return address.path("city").asString();
                        }
                        return null;
                    })
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception ex) {
            log.warn("External reverse geocoding unavailable for [{}, {}]: {}", latitude, longitude, ex.getMessage());
        }

        // Fallback: If external geocoding is null or failed, resolve state from nearest station in MongoDB
        if (state == null || state.isBlank()) {
            try {
                Query query = new Query();
                query.addCriteria(Criteria.where("location").nearSphere(new GeoJsonPoint(longitude, latitude)));
                stationEntity nearestStation = mongoTemplate.findOne(query, stationEntity.class);
                if (nearestStation != null && nearestStation.getState() != null) {
                    state = nearestStation.getState();
                    log.info("Resolved fallback state from nearest station {}: {}", nearestStation.getStationName(), state);
                }
            } catch (Exception ex) {
                log.warn("Station fallback geocoding failed for [{}, {}]: {}", latitude, longitude, ex.getMessage());
            }
        }

        if (state != null) {
            disaster.setState(state);
            disasterRepository.save(disaster);
        }
    }
}
