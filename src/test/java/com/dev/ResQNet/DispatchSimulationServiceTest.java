package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

class DispatchSimulationServiceTest {

    private dispatchRepo dispatchRepository;
    private disasterRepo disasterRepository;
    private stationRepo stationRepository;
    private userRepo userRepository;
    private SimpMessagingTemplate messagingTemplate;
    private TaskScheduler taskScheduler;
    private OsrmRouteService osrmRouteService;
    private DispatchSimulationService service;

    @BeforeEach
    void setUp() {
        dispatchRepository = mock(dispatchRepo.class);
        disasterRepository = mock(disasterRepo.class);
        stationRepository = mock(stationRepo.class);
        userRepository = mock(userRepo.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        taskScheduler = mock(TaskScheduler.class);
        osrmRouteService = mock(OsrmRouteService.class);
        service = new DispatchSimulationService(
                dispatchRepository, disasterRepository, stationRepository,
                userRepository, messagingTemplate, taskScheduler, osrmRouteService);
    }

    @Test
    void startsDispatchedMissionAndSendsOnlyToReporters() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId reporterId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        stationEntity station = station(stationId);
        disasterEntity disaster = disaster(disasterId, reporterId, null);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(disasterRepository.findById(disasterId)).thenReturn(Optional.of(disaster));
        when(disasterRepository.findByLinkedDisasterId(disasterId)).thenReturn(List.of());
        when(userRepository.findByUserId(reporterId)).thenReturn(user(reporterId, "reporter"));
        doReturn(task).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class),
                eq(Duration.ofSeconds(2)));
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.start(dispatchId, route());

        assertTrue(dispatch.getSimulationActive());
        assertEquals(1, dispatch.getRouteIndex());
        verify(messagingTemplate).convertAndSendToUser(
                eq("reporter"), eq("/queue/dispatch-location"), any(DispatchLocationDto.class));
    }

    @Test
    void oneReporterSendFailureDoesNotPreventOtherReporters() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId reporterA = new ObjectId();
        ObjectId reporterB = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId, Status.BACKUP_DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station(stationId)));
        when(disasterRepository.findById(disasterId)).thenReturn(Optional.of(disaster(disasterId, reporterA, null)));
        when(disasterRepository.findByLinkedDisasterId(disasterId)).thenReturn(
                List.of(disaster(new ObjectId(), reporterA, disasterId),
                        disaster(new ObjectId(), reporterB, disasterId)));
        when(userRepository.findByUserId(reporterA)).thenReturn(user(reporterA, "reporter-a"));
        when(userRepository.findByUserId(reporterB)).thenReturn(user(reporterB, "reporter-b"));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class),
                eq(Duration.ofSeconds(2)));
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("send failed")).when(messagingTemplate)
                .convertAndSendToUser(eq("reporter-a"), eq("/queue/dispatch-location"), any(DispatchLocationDto.class));

        service.start(dispatchId, route());

        verify(messagingTemplate).convertAndSendToUser(
                eq("reporter-b"), eq("/queue/dispatch-location"), any(DispatchLocationDto.class));
        verify(messagingTemplate, org.mockito.Mockito.times(1)).convertAndSendToUser(
                eq("reporter-a"), eq("/queue/dispatch-location"), any(DispatchLocationDto.class));
    }

    @Test
    void schedulerFailureRollsBackActiveSimulationState() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station(stationId)));
        when(disasterRepository.findById(disasterId)).thenReturn(Optional.of(disaster(disasterId, null, null)));
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class),
                eq(Duration.ofSeconds(2)))).thenThrow(new IllegalStateException("scheduler stopped"));
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertThrows(IllegalStateException.class, () -> service.start(dispatchId, route()));

        assertTrue(!Boolean.TRUE.equals(dispatch.getSimulationActive()));
        assertTrue(!service.isStartingOrRunning(dispatchId));
        verify(dispatchRepository, org.mockito.Mockito.times(2)).save(dispatch);
    }

    @Test
    void duplicateStartDoesNotCreateAnotherScheduledTask() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station(stationId)));
        when(disasterRepository.findById(disasterId)).thenReturn(Optional.of(disaster(disasterId, null, null)));
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class),
                eq(Duration.ofSeconds(2)));
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));

        service.start(dispatchId, route());
        assertThrows(IllegalStateException.class, () -> service.start(dispatchId, route()));

        verify(taskScheduler, org.mockito.Mockito.times(1)).scheduleAtFixedRate(
                any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(2)));
    }

    @Test
    void stopDispatchCancelsTaskAndDeactivatesWithoutImpactingOtherSimulations() {
        ObjectId dispatchId1 = new ObjectId();
        ObjectId dispatchId2 = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId reporterId = new ObjectId();

        dispatchEntity dispatch1 = dispatch(dispatchId1, stationId, disasterId, Status.DISPATCHED);
        dispatchEntity dispatch2 = dispatch(dispatchId2, stationId, disasterId, Status.DISPATCHED);
        stationEntity station = station(stationId);
        disasterEntity disaster = disaster(disasterId, reporterId, null);

        ScheduledFuture<?> task1 = mock(ScheduledFuture.class);
        ScheduledFuture<?> task2 = mock(ScheduledFuture.class);

        when(dispatchRepository.findById(dispatchId1)).thenReturn(Optional.of(dispatch1));
        when(dispatchRepository.findById(dispatchId2)).thenReturn(Optional.of(dispatch2));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(disasterRepository.findById(disasterId)).thenReturn(Optional.of(disaster));
        when(disasterRepository.findByLinkedDisasterId(disasterId)).thenReturn(List.of());
        when(userRepository.findByUserId(reporterId)).thenReturn(user(reporterId, "reporter"));
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));

        doReturn(task1).doReturn(task2).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(2)));

        service.start(dispatchId1, route());
        service.start(dispatchId2, route());

        assertTrue(service.isStartingOrRunning(dispatchId1));
        assertTrue(service.isStartingOrRunning(dispatchId2));

        service.stopDispatch(dispatchId1);

        verify(task1).cancel(false);
        verify(task2, never()).cancel(anyBoolean());
        assertFalse(service.isStartingOrRunning(dispatchId1));
        assertTrue(service.isStartingOrRunning(dispatchId2));
        assertFalse(dispatch1.getSimulationActive());

        dispatch1.setStatus(Status.RECALLED);
        service.broadcastStatus(dispatch1);

        org.mockito.ArgumentCaptor<DispatchLocationDto> locationCaptor =
                org.mockito.ArgumentCaptor.forClass(DispatchLocationDto.class);
        verify(messagingTemplate, org.mockito.Mockito.atLeastOnce()).convertAndSendToUser(
                eq("reporter"), eq("/queue/dispatch-location"), locationCaptor.capture());

        boolean containsRecalled = locationCaptor.getAllValues().stream()
                .anyMatch(dto -> "RECALLED".equals(dto.status()) && dispatchId1.toHexString().equals(dto.dispatchId()));
        assertTrue(containsRecalled);
    }

    private static List<List<Double>> route() {
        return List.of(List.of(0.0, 0.0), List.of(0.001, 0.0));
    }

    private static dispatchEntity dispatch(ObjectId id, ObjectId stationId, ObjectId disasterId, Status status) {
        dispatchEntity dispatch = new dispatchEntity();
        dispatch.setDispatchId(id);
        dispatch.setStationId(stationId);
        dispatch.setDisasterId(disasterId);
        dispatch.setStatus(status);
        dispatch.setSimulationActive(false);
        return dispatch;
    }

    private static stationEntity station(ObjectId id) {
        stationEntity station = new stationEntity();
        station.setStationId(id);
        station.setLocation(new GeoJsonPoint(0.0, 0.0));
        return station;
    }

    private static disasterEntity disaster(ObjectId id, ObjectId reporterId, ObjectId linkedId) {
        disasterEntity disaster = new disasterEntity();
        disaster.setDisasterId(id);
        disaster.setUserId(reporterId);
        disaster.setLinkedDisasterId(linkedId);
        disaster.setLocation(new GeoJsonPoint(0.001, 0.0));
        return disaster;
    }

    private static userEntity user(ObjectId id, String username) {
        userEntity user = new userEntity();
        user.setUserId(id);
        user.setUsername(username);
        return user;
    }
}
