
package com.dev.ResQNet;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OsrmRouteService {

    private static final String OSRM_URL =
            "https://router.project-osrm.org";

    private final WebClient webClient = WebClient.builder().build();

    public List<List<Double>> getRoute(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude) {

        validateCoordinate(startLatitude, startLongitude);
        validateCoordinate(endLatitude, endLongitude);

        String path = String.format(
                Locale.ROOT,
                "/route/v1/driving/%.7f,%.7f;%.7f,%.7f"
                        + "?overview=full&geometries=geojson",
                startLongitude,
                startLatitude,
                endLongitude,
                endLatitude
        );

        try {
            JsonNode response = webClient.get()
                    .uri(URI.create(OSRM_URL + path))
                    .header("User-Agent", "ResQNet/1.0")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(15));

            if (response == null
                    || !"Ok".equals(response.path("code").asText())) {
                throw new IllegalStateException(
                        "OSRM could not calculate the route.");
            }

            JsonNode coordinates = response
                    .path("routes")
                    .path(0)
                    .path("geometry")
                    .path("coordinates");

            if (!coordinates.isArray() || coordinates.size() < 2) {
                throw new IllegalStateException(
                        "OSRM returned an invalid route.");
            }

            if (coordinates.size() > 10000) {
                throw new IllegalStateException(
                        "OSRM route contains too many points.");
            }

            List<List<Double>> routePoints = new ArrayList<>();

            for (JsonNode point : coordinates) {
                if (!point.isArray() || point.size() < 2
                        || !point.get(0).isNumber()
                        || !point.get(1).isNumber()) {
                    throw new IllegalStateException(
                            "OSRM returned invalid coordinates.");
                }

                double longitude = point.get(0).asDouble();
                double latitude = point.get(1).asDouble();

                validateCoordinate(latitude, longitude);

                routePoints.add(List.of(longitude, latitude));
            }

            return routePoints;

        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to retrieve a route from OSRM.", ex);
        }
    }

    private void validateCoordinate(double latitude, double longitude) {
        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "Invalid geographic coordinates.");
        }
    }
}
