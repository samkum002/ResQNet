package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class DispatchWorkflowServiceTest {

    private stationServiceImpl service;
    private stationRepo stationRepository;
    private backupRepository backupRepository;
    private resourceRepository resourceRepository;
    private dispatchRepo dispatchRepository;
    private userRepo userRepository;
    private disasterRepo disasterRepository;
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        service = new stationServiceImpl();
        stationRepository = mock(stationRepo.class);
        backupRepository = mock(backupRepository.class);
        resourceRepository = mock(resourceRepository.class);
        dispatchRepository = mock(dispatchRepo.class);
        userRepository = mock(userRepo.class);
        disasterRepository = mock(disasterRepo.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        ReflectionTestUtils.setField(service, "stationRepository", stationRepository);
        ReflectionTestUtils.setField(service, "backupRepo", backupRepository);
        ReflectionTestUtils.setField(service, "resourceRepo", resourceRepository);
        ReflectionTestUtils.setField(service, "dispatchRepository", dispatchRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "disasterRepository", disasterRepository);
        ReflectionTestUtils.setField(service, "messagingTemplate", messagingTemplate);
    }

    @Test
    void backupRequestPersistsPendingBackupAndTransitionsDispatchAndDisaster() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId stationManagerId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        userEntity worker = worker(stationId);
        stationEntity station = new stationEntity();
        station.setStationId(stationId);
        station.setUserId(stationManagerId);
        station.setForceType(Forces.SDRF);
        disasterEntity disaster = new disasterEntity();
        disaster.setDisasterId(disasterId);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(userRepository.findByUsername("worker")).thenReturn(worker);
        userEntity managerUser = new userEntity();
        managerUser.setUserId(stationManagerId);
        managerUser.setUsername("manager-user");
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(userRepository.findById(stationManagerId)).thenReturn(Optional.of(managerUser));
        when(disasterRepository.findByDisasterId(disasterId)).thenReturn(disaster);

        var response = service.requestBackup("worker", backupDto(2, 5), dispatchId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.BACKUP_REQUEST, dispatch.getStatus());
        assertEquals(Status.BACKUP_REQUEST, disaster.getStatus());
        org.mockito.ArgumentCaptor<backupEntity> backupCaptor =
                org.mockito.ArgumentCaptor.forClass(backupEntity.class);
        verify(backupRepository).save(backupCaptor.capture());
        backupEntity savedBackup = backupCaptor.getValue();
        assertEquals(dispatchId, savedBackup.getDispatchId());
        assertEquals(disasterId, savedBackup.getDisasterId());
        assertEquals(backupStatus.PENDING, savedBackup.getStatus());
        assertEquals(2, savedBackup.getReqVehicles());
        assertEquals(5, savedBackup.getReqPersonnel());
        verify(dispatchRepository).save(dispatch);
        verify(disasterRepository).save(disaster);
        verify(messagingTemplate).convertAndSendToUser(
                eq("manager-user"), eq("/update/mission"), any(dispatchDto.class));
    }

    @Test
    void backupRequestRejectsNonDispatchedMissionWithoutPersistence() {
        ObjectId dispatchId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, new ObjectId(), new ObjectId(), Status.VERIFIED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        var response = service.requestBackup("worker", backupDto(1, 1), dispatchId);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(backupRepository, never()).save(any(backupEntity.class));
        verify(dispatchRepository, never()).save(any(dispatchEntity.class));
    }

    @Test
    void normalDispatchCompletionMovesOnlyToDispatchCompletedStage() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, new ObjectId(), Status.DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(userRepository.findByUsername("worker")).thenReturn(worker(stationId));

        var response = service.completeDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.DISPATCH_COMPLETED, dispatch.getStatus());
        verify(dispatchRepository).save(dispatch);
        verify(resourceRepository, never()).save(any(resourceEntity.class));
    }

    @Test
    void backupDispatchCompletionUpdatesBothDispatchAndBackupRecord() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, new ObjectId(), Status.BACKUP_DISPATCHED);
        backupEntity backup = new backupEntity();
        backup.setStatus(backupStatus.DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(userRepository.findByUsername("worker")).thenReturn(worker(stationId));
        when(backupRepository.findByDispatchId(dispatchId)).thenReturn(backup);

        var response = service.completeDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.BACKUP_DISPATCH_COMPLETED, dispatch.getStatus());
        assertEquals(backupStatus.BACKUP_DISPATCH_COMPLETED, backup.getStatus());
        verify(dispatchRepository).save(dispatch);
        verify(backupRepository).save(backup);
    }

    @Test
    void falseDispatchMarksOnlyAnAuthorizedDispatchedMission() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, new ObjectId(), Status.DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(userRepository.findByUsername("worker")).thenReturn(worker(stationId));

        var response = service.falseDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.DISPATCH_FALSE, dispatch.getStatus());
        verify(dispatchRepository).save(dispatch);
    }

    @Test
    void falseDispatchRejectsWrongStationWithoutChangingStatus() {
        ObjectId dispatchId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, new ObjectId(), new ObjectId(), Status.DISPATCHED);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(userRepository.findByUsername("worker")).thenReturn(worker(new ObjectId()));

        var response = service.falseDispatch(dispatchId, "worker");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Status.DISPATCHED, dispatch.getStatus());
        verify(dispatchRepository, never()).save(any(dispatchEntity.class));
    }

    @Test
    void stationFakeWorkflowReleasesAssignedResourcesAndReturnsWorkerToAvailable() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId workerId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId, Status.DISPATCH_FALSE);
        dispatch.setWorkerId(workerId);
        dispatch.setAssignedPersonnel(3);
        dispatch.setAssignedVehicle(2);
        userEntity worker = worker(stationId);
        worker.setUserId(workerId);
        worker.setStats(new Stats(0, 0, 0, 0));
        stationEntity station = new stationEntity();
        station.setStationId(stationId);
        station.setUserId(new ObjectId());
        resourceEntity resources = new resourceEntity();
        resources.setAvailablePersonnel(4);
        resources.setAvailableVehicle(1);
        disasterEntity disaster = new disasterEntity();
        disaster.setDisasterId(disasterId);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(userRepository.findByUsername("worker")).thenReturn(worker);
        when(stationRepository.findByUserId(workerId)).thenReturn(station);
        when(resourceRepository.findByStationId(stationId)).thenReturn(resources);
        when(disasterRepository.findByDisasterId(disasterId)).thenReturn(disaster);
        when(dispatchRepository.findByDisasterIdAndStatus(disasterId, Status.DISPATCHED))
                .thenReturn(java.util.List.of(dispatch));
        when(userRepository.findById(workerId)).thenReturn(Optional.of(worker));

        var response = service.fakeMission(dispatchId, "worker");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.DISPATCH_CANCELLED, dispatch.getStatus());
        assertEquals(7, resources.getAvailablePersonnel());
        assertEquals(3, resources.getAvailableVehicle());
        assertEquals(Admin.AVAILABLE, worker.getWorkerStatus());
        assertEquals(1, worker.getStats().getMarkedFalse());
        assertEquals(1, worker.getStats().getTotalAssigned());
        verify(resourceRepository).save(resources);
        verify(dispatchRepository).save(dispatch);
    }

    @Test
    void nonexistentDispatchIsNotSilentlyTreatedAsSuccess() {
        ObjectId missingId = new ObjectId();
        when(dispatchRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.completeDispatch(missingId, "worker"));
    }

    private static backupDTO backupDto(int vehicles, int personnel) {
        backupDTO dto = new backupDTO();
        dto.setReqVehicles(vehicles);
        dto.setReqPersonnel(personnel);
        return dto;
    }

    private static userEntity worker(ObjectId stationId) {
        userEntity worker = new userEntity();
        worker.setUserId(new ObjectId());
        worker.setUsername("worker");
        worker.setStationId(stationId);
        worker.setRoles(java.util.List.of("WORKER"));
        return worker;
    }

    private static dispatchEntity dispatch(ObjectId id, ObjectId stationId, ObjectId disasterId, Status status) {
        dispatchEntity dispatch = new dispatchEntity();
        dispatch.setDispatchId(id);
        dispatch.setStationId(stationId);
        dispatch.setDisasterId(disasterId);
        dispatch.setStatus(status);
        return dispatch;
    }
}
