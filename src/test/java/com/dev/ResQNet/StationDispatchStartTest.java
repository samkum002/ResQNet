package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StationDispatchStartTest {

    private stationRepo stationRepository;
    private backupRepository backupRepository;
    private resourceRepository resourceRepository;
    private dispatchRepo dispatchRepository;
    private userRepo userRepository;
    private disasterRepo disasterRepository;
    private DispatchSimulationService simulationService;
    private stationServiceImpl service;

    @BeforeEach
    void setUp() {
        stationRepository = mock(stationRepo.class);
        backupRepository = mock(backupRepository.class);
        resourceRepository = mock(resourceRepository.class);
        dispatchRepository = mock(dispatchRepo.class);
        userRepository = mock(userRepo.class);
        disasterRepository = mock(disasterRepo.class);
        simulationService = mock(DispatchSimulationService.class);
        service = new stationServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "stationRepository", stationRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "backupRepo", backupRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resourceRepo", resourceRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dispatchRepository", dispatchRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userRepository", userRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "disasterRepository", disasterRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "simulationService", simulationService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "messagingTemplate", mock(org.springframework.messaging.simp.SimpMessagingTemplate.class));
    }

    @Test
    void normalStartTransitionsDispatchAndStartsSimulation() {
        ObjectId dispatchId = new ObjectId();
        userEntity worker = worker();
        dispatchEntity dispatch = dispatch(dispatchId, worker, Status.DISPATCH_ASSIGNED);
        List<List<Double>> route = List.of(List.of(0.0, 0.0), List.of(0.0002, 0.0));
        when(userRepository.findByUsername("worker")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(simulationService.calculateRoute(dispatchId)).thenReturn(route);
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.startDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.DISPATCHED, dispatch.getStatus());
        assertTrue(dispatch.getDispatchedAt() != null);
        verify(simulationService).start(dispatchId, route);
    }

    @Test
    void backupStartTransitionsDispatchAndBackupRecord() {
        ObjectId dispatchId = new ObjectId();
        userEntity worker = worker();
        dispatchEntity dispatch = dispatch(dispatchId, worker, Status.BACKUP_ASSIGNED);
        backupEntity backup = new backupEntity();
        backup.setStatus(backupStatus.ASSIGNED);
        List<List<Double>> route = List.of(List.of(0.0, 0.0), List.of(0.0002, 0.0));
        when(userRepository.findByUsername("worker")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(backupRepository.findByDispatchId(dispatchId)).thenReturn(backup);
        when(simulationService.calculateRoute(dispatchId)).thenReturn(route);
        when(dispatchRepository.save(any(dispatchEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(backupRepository.save(any(backupEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.startDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.BACKUP_DISPATCHED, dispatch.getStatus());
        assertEquals(backupStatus.DISPATCHED, backup.getStatus());
        assertTrue(dispatch.getDispatchedAt() == null);
        verify(simulationService).start(dispatchId, route);
    }

    @Test
    void getAssignedDispatchesReturnsBothAssignedAndDispatchedMissions() {
        userEntity worker = worker();
        when(userRepository.findByUsername("worker")).thenReturn(worker);

        dispatchEntity assigned = dispatch(new ObjectId(), worker, Status.DISPATCH_ASSIGNED);
        assigned.setSeverity(Severity.HIGH);
        assigned.setForceType(Forces.FIRE_DEPARTMENT);

        dispatchEntity dispatched = dispatch(new ObjectId(), worker, Status.DISPATCHED);
        dispatched.setSeverity(Severity.CRITICAL);
        dispatched.setForceType(Forces.POLICE_STATION);

        when(dispatchRepository.findByWorkerIdAndStatusIn(eq(worker.getUserId()), any()))
                .thenReturn(List.of(assigned, dispatched));
        when(backupRepository.findByWorkerIdAndStatusIn(eq(worker.getUserId()), any()))
                .thenReturn(List.of());

        var response = service.getAssignedDispatches("worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        dispatches result = (dispatches) response.getBody();
        assertEquals(2, result.getDispatch().size());
        assertEquals(Status.DISPATCH_ASSIGNED, result.getDispatch().get(0).getStatus());
        assertEquals(Status.DISPATCHED, result.getDispatch().get(1).getStatus());
    }

    @Test
    void startDispatchReturns502WithoutModifyingStatusWhenRouteCalculationFails() {
        ObjectId dispatchId = new ObjectId();
        userEntity worker = worker();
        dispatchEntity dispatch = dispatch(dispatchId, worker, Status.DISPATCH_ASSIGNED);

        when(userRepository.findByUsername("worker")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(simulationService.calculateRoute(dispatchId))
                .thenThrow(new IllegalStateException("OSRM route unavailable: NoRoute"));

        var response = service.startDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("OSRM route unavailable: NoRoute"));
        assertEquals(Status.DISPATCH_ASSIGNED, dispatch.getStatus());
        assertTrue(dispatch.getDispatchedAt() == null);
    }

    private userEntity worker() {
        userEntity worker = new userEntity();
        worker.setUserId(new ObjectId());
        worker.setUsername("worker");
        return worker;
    }

    private dispatchEntity dispatch(ObjectId id, userEntity worker, Status status) {
        dispatchEntity dispatch = new dispatchEntity();
        dispatch.setDispatchId(id);
        dispatch.setWorkerId(worker.getUserId());
        dispatch.setStatus(status);
        return dispatch;
    }
}
