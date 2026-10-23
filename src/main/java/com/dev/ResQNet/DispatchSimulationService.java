
package com.dev.ResQNet;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class DispatchSimulationService {

    private static final Logger log =
            LoggerFactory.getLogger(DispatchSimulationService.class);

    private static final int MAX_ROUTE_POINTS = 10000;
    private static final double MAX_ENDPOINT_DISTANCE_METERS = 2000;
    private static final double SIMULATED_SPEED_METERS_PER_SECOND = 12.0;
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(2);
    private static final long USERNAME_CACHE_TTL_NANOS =
            Duration.ofMinutes(5).toNanos();
    private static final long USERNAME_CACHE_CLEANUP_INTERVAL_NANOS =
            Duration.ofMinutes(1).toNanos();

    private final dispatchRepo dispatchRepository;
    private final disasterRepo disasterRepository;
    private final stationRepo stationRepository;
    private final userRepo userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    private final OsrmRouteService osrmRouteService;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTasks =
            new ConcurrentHashMap<>();

    private final Set<String> startingDispatches =
            ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<ObjectId, CachedUsername> usernameCache =
            new ConcurrentHashMap<>();
    private final AtomicLong nextUsernameCacheCleanupNanos =
            new AtomicLong(Long.MIN_VALUE);

    private record CachedUsername(String username, long expiresAtNanos) {}

    public DispatchSimulationService(
            dispatchRepo dispatchRepository,
            disasterRepo disasterRepository,
            stationRepo stationRepository,
            userRepo userRepository,
            SimpMessagingTemplate messagingTemplate,
            TaskScheduler taskScheduler,
            OsrmRouteService osrmRouteService) {

        this.dispatchRepository = dispatchRepository;
        this.disasterRepository = disasterRepository;
        this.stationRepository = stationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
        this.osrmRouteService = osrmRouteService;
    }

    public List<List<Double>> calculateRoute(ObjectId dispatchId) {
        dispatchEntity dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Dispatch not found."));

        if (dispatch.getStationId() == null
                || dispatch.getDisasterId() == null) {
            throw new IllegalStateException(
                    "Dispatch has no assigned station or disaster.");
        }

        stationEntity station = stationRepository.findById(dispatch.getStationId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Assigned station not found."));

        disasterEntity disaster = disasterRepository.findById(dispatch.getDisasterId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Disaster not found."));

        if (station.getLocation() == null
                || disaster.getLocation() == null) {
            throw new IllegalStateException(
                    "Station or disaster location is missing.");
        }

        var stationLocation = station.getLocation();
        var disasterLocation = disaster.getLocation();

        validateCoordinate(stationLocation.getY(), stationLocation.getX());
        validateCoordinate(disasterLocation.getY(), disasterLocation.getX());

        List<List<Double>> routePoints = osrmRouteService.getRoute(
                stationLocation.getY(),
                stationLocation.getX(),
                disasterLocation.getY(),
                disasterLocation.getX());

        validateRoute(routePoints);
        return routePoints;
    }

    public void start(ObjectId dispatchId, List<List<Double>> routePoints) {
        validateRoute(routePoints);

        String key = dispatchId.toHexString();

        if (activeTasks.containsKey(key) || !startingDispatches.add(key)) {
            throw new IllegalStateException(
                    "Simulation is already scheduled.");
        }

        try {
            dispatchEntity dispatch = dispatchRepository.findById(dispatchId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Dispatch not found."));

            if (Boolean.TRUE.equals(dispatch.getSimulationActive())) {
                throw new IllegalStateException(
                        "Simulation is already running.");
            }

            if (dispatch.getStatus() != Status.DISPATCHED
                    && dispatch.getStatus() != Status.BACKUP_DISPATCHED) {
                throw new IllegalStateException(
                        "Dispatch must be started through its assigned workflow.");
            }

            validateRouteEndpoints(dispatch, routePoints);

            List<List<Double>> storedRoute = new ArrayList<>();

            for (List<Double> point : routePoints) {
                storedRoute.add(List.of(point.get(0), point.get(1)));
            }

            List<List<Double>> previousRoute = dispatch.getRoutePoints();
            Integer previousIndex = dispatch.getRouteIndex();
            Double previousLatitude = dispatch.getCurrentLatitude();
            Double previousLongitude = dispatch.getCurrentLongitude();
            Boolean previousActive = dispatch.getSimulationActive();
            LocalDateTime previousStartedAt = dispatch.getSimulationStartedAt();
            LocalDateTime previousArrivedAt = dispatch.getArrivedAt();
            LocalDateTime previousUpdateAt = dispatch.getLastLocationUpdateAt();

            List<Double> first = storedRoute.get(0);
            LocalDateTime now = LocalDateTime.now();

            dispatch.setRoutePoints(storedRoute);
            dispatch.setRouteIndex(1);
            dispatch.setCurrentLongitude(first.get(0));
            dispatch.setCurrentLatitude(first.get(1));
            dispatch.setSimulationActive(true);
            dispatch.setSimulationStartedAt(now);
            dispatch.setArrivedAt(null);
            dispatch.setLastLocationUpdateAt(now);

            try {
                dispatchRepository.save(dispatch);
                schedule(dispatchId);
            } catch (RuntimeException ex) {
                stop(dispatchId);

                dispatch.setRoutePoints(previousRoute);
                dispatch.setRouteIndex(previousIndex);
                dispatch.setCurrentLatitude(previousLatitude);
                dispatch.setCurrentLongitude(previousLongitude);
                dispatch.setSimulationActive(
                        Boolean.TRUE.equals(previousActive));
                dispatch.setSimulationStartedAt(previousStartedAt);
                dispatch.setArrivedAt(previousArrivedAt);
                dispatch.setLastLocationUpdateAt(previousUpdateAt);

                try {
                    dispatchRepository.save(dispatch);
                } catch (RuntimeException rollbackException) {
                    ex.addSuppressed(rollbackException);
                }

                throw new IllegalStateException(
                        "Could not schedule dispatch simulation.", ex);
            }

            broadcastSafely(dispatch, false);
        } finally {
            startingDispatches.remove(key);
        }
    }

    public boolean isStartingOrRunning(ObjectId dispatchId) {
        String key = dispatchId.toHexString();
        return startingDispatches.contains(key) || activeTasks.containsKey(key);
    }

    /**
     * Cancels the running simulation task for a specific dispatch and marks
     * {@code simulationActive = false} in MongoDB so the advance loop cannot
     * restart itself. Safe to call when no simulation is running.
     *
     * @param dispatchId the dispatch whose simulation should be cancelled
     */
    public void stopDispatch(ObjectId dispatchId) {
        stop(dispatchId);
        markInactiveSafely(dispatchId);
    }

    /**
     * Publishes a location/status update event for the given dispatch using
     * the existing simulation broadcast pipeline. Used to notify subscribers
     * of terminal status changes (such as RECALLED) so maps and dashboards
     * can remove or update the active marker.
     *
     * @param dispatch the dispatch whose current location and status should be broadcast
     */
    public void broadcastStatus(dispatchEntity dispatch) {
        broadcastSafely(dispatch, false);
    }

    private void schedule(ObjectId dispatchId) {
        String key = dispatchId.toHexString();
        try {
            activeTasks.computeIfAbsent(key, ignored -> {
                ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(
                        () -> advance(dispatchId),
                        Instant.now().plus(TICK_INTERVAL),
                        TICK_INTERVAL);
                if (task == null) {
                    throw new IllegalStateException(
                            "Scheduler did not create a simulation task.");
                }
                return task;
            });
        } catch (RuntimeException ex) {
            if (activeTasks.containsKey(key)) {
                throw new IllegalStateException(
                        "Simulation is already scheduled.", ex);
            }
            throw ex;
        }

        if (!activeTasks.containsKey(key)) {
            throw new IllegalStateException(
                    "Scheduler did not register a simulation task.");
        }
    }

    private void advance(ObjectId dispatchId) {
        try {
            dispatchEntity dispatch = dispatchRepository.findById(dispatchId)
                    .orElse(null);

            if (dispatch == null
                    || !Boolean.TRUE.equals(dispatch.getSimulationActive())) {
                stop(dispatchId);
                return;
            }

            if (dispatch.getStatus() != Status.DISPATCHED
                    && dispatch.getStatus() != Status.BACKUP_DISPATCHED) {
                deactivate(dispatch);
                stop(dispatchId);
                return;
            }

            List<List<Double>> points = dispatch.getRoutePoints();

            if (!isValidRoute(points)
                    || dispatch.getCurrentLatitude() == null
                    || dispatch.getCurrentLongitude() == null) {
                deactivate(dispatch);
                stop(dispatchId);
                return;
            }

            double latitude = dispatch.getCurrentLatitude();
            double longitude = dispatch.getCurrentLongitude();

            int targetIndex = dispatch.getRouteIndex() == null
                    ? 1 : dispatch.getRouteIndex();

            if (targetIndex < 1 || targetIndex > points.size()) {
                deactivate(dispatch);
                stop(dispatchId);
                return;
            }

            double remainingMeters =
                    SIMULATED_SPEED_METERS_PER_SECOND
                            * TICK_INTERVAL.toMillis() / 1000.0;

            while (remainingMeters > 0 && targetIndex < points.size()) {
                List<Double> target = points.get(targetIndex);

                double targetLongitude = target.get(0);
                double targetLatitude = target.get(1);

                double distance = distanceMeters(
                        latitude, longitude,
                        targetLatitude, targetLongitude);

                if (distance < 0.01) {
                    latitude = targetLatitude;
                    longitude = targetLongitude;
                    targetIndex++;
                    continue;
                }

                if (remainingMeters >= distance) {
                    latitude = targetLatitude;
                    longitude = targetLongitude;
                    remainingMeters -= distance;
                    targetIndex++;
                } else {
                    double fraction = remainingMeters / distance;

                    latitude += (targetLatitude - latitude) * fraction;
                    longitude += (targetLongitude - longitude) * fraction;

                    remainingMeters = 0;
                }
            }

            dispatch.setCurrentLatitude(latitude);
            dispatch.setCurrentLongitude(longitude);
            dispatch.setRouteIndex(targetIndex);
            dispatch.setLastLocationUpdateAt(LocalDateTime.now());

            boolean arrived = targetIndex >= points.size();

            if (arrived) {
                dispatch.setSimulationActive(false);
                dispatch.setArrivedAt(LocalDateTime.now());
            }

            dispatchRepository.save(dispatch);
            broadcastSafely(dispatch, arrived);

            if (arrived) {
                stop(dispatchId);
            }

        } catch (Exception ex) {
            log.error("Simulation failed for dispatch {}", dispatchId, ex);
            stop(dispatchId);
            markInactiveSafely(dispatchId);
        }
    }

    private void validateRouteEndpoints(
            dispatchEntity dispatch,
            List<List<Double>> routePoints) {

        stationEntity station = stationRepository.findById(dispatch.getStationId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Station not found."));

        disasterEntity disaster = disasterRepository.findById(dispatch.getDisasterId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Disaster not found."));

        if (station.getLocation() == null
                || disaster.getLocation() == null) {
            throw new IllegalStateException(
                    "Station or disaster location is missing.");
        }

        List<Double> first = routePoints.get(0);
        List<Double> last = routePoints.get(routePoints.size() - 1);

        if (distanceMeters(
                station.getLocation().getY(),
                station.getLocation().getX(),
                first.get(1),
                first.get(0)) > MAX_ENDPOINT_DISTANCE_METERS) {
            throw new IllegalArgumentException(
                    "Route does not begin near the assigned station.");
        }

        if (distanceMeters(
                disaster.getLocation().getY(),
                disaster.getLocation().getX(),
                last.get(1),
                last.get(0)) > MAX_ENDPOINT_DISTANCE_METERS) {
            throw new IllegalArgumentException(
                    "Route does not end near the disaster.");
        }
    }

    private void validateRoute(List<List<Double>> routePoints) {
        if (!isValidRoute(routePoints)) {
            throw new IllegalArgumentException(
                    "Route must contain between 2 and "
                            + MAX_ROUTE_POINTS
                            + " valid coordinate points.");
        }
    }

    private boolean isValidRoute(List<List<Double>> routePoints) {
        if (routePoints == null
                || routePoints.size() < 2
                || routePoints.size() > MAX_ROUTE_POINTS) {
            return false;
        }

        for (List<Double> point : routePoints) {
            if (point == null
                    || point.size() != 2
                    || point.get(0) == null
                    || point.get(1) == null) {
                return false;
            }

            double longitude = point.get(0);
            double latitude = point.get(1);

            if (!Double.isFinite(longitude)
                    || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180
                    || latitude < -90 || latitude > 90) {
                return false;
            }
        }

        return true;
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

    private void deactivate(dispatchEntity dispatch) {
        dispatch.setSimulationActive(false);
        dispatch.setLastLocationUpdateAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);
    }

    private void markInactiveSafely(ObjectId dispatchId) {
        try {
            dispatchRepository.findById(dispatchId).ifPresent(dispatch -> {
                dispatch.setSimulationActive(false);
                dispatch.setLastLocationUpdateAt(LocalDateTime.now());
                dispatchRepository.save(dispatch);
            });
        } catch (Exception ex) {
            log.error(
                    "Could not deactivate failed simulation {}",
                    dispatchId, ex);
        }
    }

    private void broadcastSafely(
            dispatchEntity dispatch, boolean arrived) {
        try {
            broadcast(dispatch, arrived);
        } catch (Exception ex) {
            log.warn(
                    "Location broadcast failed for dispatch {}",
                    dispatch.getDispatchId(), ex);
        }
    }

    private void broadcast(dispatchEntity dispatch, boolean arrived) {
        if (dispatch.getDisasterId() == null
                || dispatch.getDispatchId() == null) {
            return;
        }

        DispatchLocationDto update = new DispatchLocationDto(
                dispatch.getDispatchId().toHexString(),
                dispatch.getDisasterId().toHexString(),
                dispatch.getWorkerId() == null
                        ? null : dispatch.getWorkerId().toHexString(),
                dispatch.getCurrentLatitude(),
                dispatch.getCurrentLongitude(),
                dispatch.getRouteIndex(),
                dispatch.getRoutePoints() == null
                        ? 0 : dispatch.getRoutePoints().size(),
                dispatch.getStatus() == null
                        ? null : dispatch.getStatus().name(),
                arrived,
                dispatch.getLastLocationUpdateAt());

        for (ObjectId reporterId :
                findRelatedReporterIds(dispatch.getDisasterId())) {

            String username = findReporterUsername(reporterId);

            if (username == null || username.isBlank()) {
                continue;
            }

            try {
                messagingTemplate.convertAndSendToUser(
                        username,
                        "/queue/dispatch-location",
                        update);
            } catch (Exception ex) {
                log.warn("Location broadcast failed for reporter {}", reporterId, ex);
            }
        }
    }

    private String findReporterUsername(ObjectId reporterId) {
        long now = System.nanoTime();
        cleanupExpiredUsernames(now);
        CachedUsername cached = usernameCache.get(reporterId);

        if (cached != null && now - cached.expiresAtNanos() < 0) {
            return cached.username();
        }

        userEntity reporter = userRepository.findByUserId(reporterId);
        String username = reporter == null ? null : reporter.getUsername();

        usernameCache.put(
                reporterId,
                new CachedUsername(
                        username,
                        now + USERNAME_CACHE_TTL_NANOS));

        return username;
    }

    private void cleanupExpiredUsernames(long now) {
        long nextCleanup = nextUsernameCacheCleanupNanos.get();
        if (nextCleanup != Long.MIN_VALUE && now - nextCleanup < 0) {
            return;
        }

        if (!nextUsernameCacheCleanupNanos.compareAndSet(
                nextCleanup, now + USERNAME_CACHE_CLEANUP_INTERVAL_NANOS)) {
            return;
        }

        usernameCache.forEach((userId, cached) -> {
            if (now - cached.expiresAtNanos() >= 0) {
                usernameCache.remove(userId, cached);
            }
        });
    }

    private Set<ObjectId> findRelatedReporterIds(ObjectId dispatchDisasterId) {
        Set<ObjectId> reporterIds = new LinkedHashSet<>();

        disasterEntity disaster = disasterRepository.findById(dispatchDisasterId)
                .orElse(null);

        if (disaster == null) {
            return reporterIds;
        }

        ObjectId originalDisasterId =
                disaster.getLinkedDisasterId() != null
                        ? disaster.getLinkedDisasterId()
                        : disaster.getDisasterId();

        (originalDisasterId.equals(dispatchDisasterId)
                ? java.util.Optional.of(disaster)
                : disasterRepository.findById(originalDisasterId))
                .map(disasterEntity::getUserId)
                .ifPresent(reporterIds::add);

        for (disasterEntity linkedDisaster :
                disasterRepository.findByLinkedDisasterId(originalDisasterId)) {

            if (linkedDisaster.getUserId() != null) {
                reporterIds.add(linkedDisaster.getUserId());
            }
        }

        return reporterIds;
    }

    private void stop(ObjectId dispatchId) {
        String key = dispatchId.toHexString();
        ScheduledFuture<?> task = activeTasks.get(key);

        if (task != null) {
            try {
                task.cancel(false);
                activeTasks.remove(key, task);
            } catch (RuntimeException ex) {
                log.error(
                        "Could not cancel simulation task for dispatch {}",
                        dispatchId, ex);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveSimulations() {
        List<dispatchEntity> activeDispatches;

        try {
            activeDispatches =
                    dispatchRepository.findBySimulationActiveTrue();
        } catch (Exception ex) {
            log.error("Could not load active simulations for recovery.", ex);
            return;
        }

        for (dispatchEntity dispatch : activeDispatches) {
            try {
                if (dispatch.getDispatchId() == null
                        || !isValidRoute(dispatch.getRoutePoints())
                        || (dispatch.getStatus() != Status.DISPATCHED
                        && dispatch.getStatus() != Status.BACKUP_DISPATCHED)) {
                    deactivate(dispatch);
                    continue;
                }

                if (dispatch.getRouteIndex() != null
                        && (dispatch.getRouteIndex() < 1
                        || dispatch.getRouteIndex()
                        > dispatch.getRoutePoints().size())) {
                    deactivate(dispatch);
                    continue;
                }

                if (dispatch.getRouteIndex() == null) {
                    dispatch.setRouteIndex(1);
                    dispatchRepository.save(dispatch);
                }

                if (dispatch.getCurrentLatitude() == null
                        || dispatch.getCurrentLongitude() == null) {
                    deactivate(dispatch);
                    continue;
                }

                schedule(dispatch.getDispatchId());

                log.info(
                        "Recovered simulation for dispatch {}",
                        dispatch.getDispatchId());

            } catch (Exception ex) {
                log.error(
                        "Could not recover simulation for dispatch {}",
                        dispatch.getDispatchId(), ex);

                try {
                    deactivate(dispatch);
                } catch (Exception saveException) {
                    log.error(
                            "Could not deactivate unrecoverable simulation {}",
                            dispatch.getDispatchId(), saveException);
                }
            }
        }
    }

    private double distanceMeters(
            double lat1, double lon1,
            double lat2, double lon2) {

        double earthRadius = 6_371_000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return earthRadius * 2
                * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
