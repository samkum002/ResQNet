package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;

class RecallWorkflowServiceTest {

    private stationServiceImpl service;
    private stationRepo stationRepository;
    private backupRepository backupRepository;
    private resourceRepository resourceRepository;
    private dispatchRepo dispatchRepository;
    private userRepo userRepository;
    private disasterRepo disasterRepository;
    private recallRequestRepo recallRequestRepository;
    private DispatchSimulationService simulationService;
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
        recallRequestRepository = mock(recallRequestRepo.class);
        simulationService = mock(DispatchSimulationService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        ReflectionTestUtils.setField(service, "stationRepository", stationRepository);
        ReflectionTestUtils.setField(service, "backupRepo", backupRepository);
        ReflectionTestUtils.setField(service, "resourceRepo", resourceRepository);
        ReflectionTestUtils.setField(service, "dispatchRepository", dispatchRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "disasterRepository", disasterRepository);
        ReflectionTestUtils.setField(service, "recallRequestRepository", recallRequestRepository);
        ReflectionTestUtils.setField(service, "simulationService", simulationService);
        ReflectionTestUtils.setField(service, "messagingTemplate", messagingTemplate);
    }

    @Test
    void workerCreatesValidRecallRequestAndNotifiesManager() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId workerId = new ObjectId();
        ObjectId managerUserId = new ObjectId();

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        dispatch.setForceType(Forces.FIRE_DEPARTMENT);

        userEntity worker = createWorker(workerId, stationId, "worker1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);
        userEntity manager = createManager(managerUserId, "manager1");

        when(userRepository.findByUsername("worker1")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(recallRequestRepository.findByDispatchIdAndStatus(dispatchId, recallStatus.PENDING))
                .thenReturn(Optional.empty());
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(userRepository.findById(managerUserId)).thenReturn(Optional.of(manager));

        var response = service.requestRecall("worker1", dispatchId, "Fire is extinguished; assistance not needed.");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<recallRequestEntity> captor = ArgumentCaptor.forClass(recallRequestEntity.class);
        verify(recallRequestRepository).save(captor.capture());
        recallRequestEntity saved = captor.getValue();

        assertEquals(dispatchId, saved.getDispatchId());
        assertEquals(disasterId, saved.getDisasterId());
        assertEquals(stationId, saved.getStationId());
        assertEquals(workerId, saved.getRequestingWorkerId());
        assertEquals("worker1", saved.getRequestingWorkerUsername());
        assertEquals(Forces.FIRE_DEPARTMENT, saved.getForceType());
        assertEquals("Fire is extinguished; assistance not needed.", saved.getReason());
        assertEquals(recallStatus.PENDING, saved.getStatus());

        verify(messagingTemplate).convertAndSendToUser(
                eq("manager1"), eq("/update/mission"), any(recallRequestDto.class));
    }

    @Test
    void unauthorizedWorkerCannotRequestRecallForAnotherStationDispatch() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationA = new ObjectId();
        ObjectId stationB = new ObjectId();

        dispatchEntity dispatch = createDispatch(dispatchId, stationA, new ObjectId(), Status.DISPATCHED);
        userEntity worker = createWorker(new ObjectId(), stationB, "workerFromOtherStation");

        when(userRepository.findByUsername("workerFromOtherStation")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        var response = service.requestRecall("workerFromOtherStation", dispatchId, "Cancel please");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(recallRequestRepository, never()).save(any(recallRequestEntity.class));
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void cannotRequestRecallForNonDispatchedDispatch() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, new ObjectId(), Status.VERIFIED);
        userEntity worker = createWorker(new ObjectId(), stationId, "worker1");

        when(userRepository.findByUsername("worker1")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        var response = service.requestRecall("worker1", dispatchId, "Not needed");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(recallRequestRepository, never()).save(any(recallRequestEntity.class));
    }

    @Test
    void preventDuplicatePendingRecallRequests() {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, new ObjectId(), Status.DISPATCHED);
        userEntity worker = createWorker(new ObjectId(), stationId, "worker1");

        when(userRepository.findByUsername("worker1")).thenReturn(worker);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(recallRequestRepository.findByDispatchIdAndStatus(dispatchId, recallStatus.PENDING))
                .thenReturn(Optional.of(new recallRequestEntity()));

        var response = service.requestRecall("worker1", dispatchId, "Second request");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(recallRequestRepository, never()).save(any(recallRequestEntity.class));
    }

    @Test
    void managerRejectionLeavesDispatchResourcesAndSimulationUnchanged() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId managerUserId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setRequestingWorkerUsername("worker1");
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.FIRE_DEPARTMENT);

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, new ObjectId(), Status.DISPATCHED);
        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        var response = service.rejectRecall("manager1", recallId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(recallStatus.REJECTED, recall.getStatus());
        verify(recallRequestRepository).save(recall);

        assertEquals(Status.DISPATCHED, dispatch.getStatus());
        verify(dispatchRepository, never()).save(any(dispatchEntity.class));
        verify(resourceRepository, never()).save(any(resourceEntity.class));
        verify(simulationService, never()).stopDispatch(any());

        verify(messagingTemplate).convertAndSendToUser(
                eq("worker1"), eq("/new/mission"), any(recallRequestDto.class));
    }

    @Test
    void managerApprovalSetsDispatchToRecalledAndRestoresResourcesExactlyOnce() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId managerUserId = new ObjectId();
        ObjectId workerId = new ObjectId();
        ObjectId adminId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setDisasterId(disasterId);
        recall.setRequestingWorkerUsername("worker1");
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.FIRE_DEPARTMENT);

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        dispatch.setAssignedVehicle(2);
        dispatch.setAssignedPersonnel(4);
        dispatch.setWorkerId(workerId);
        dispatch.setSimulationActive(true);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);

        resourceEntity resource = new resourceEntity();
        resource.setStationId(stationId);
        resource.setAvailableVehicle(3);
        resource.setAvailablePersonnel(5);

        userEntity worker = createWorker(workerId, stationId, "worker1");
        worker.setWorkerStatus(Admin.BUSY);

        disasterEntity disaster = new disasterEntity();
        disaster.setDisasterId(disasterId);
        disaster.setAssignedAdminId(adminId);
        disaster.setStatus(Status.DISPATCHED);

        userEntity admin = new userEntity();
        admin.setUserId(adminId);
        admin.setUsername("admin1");

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(resourceRepository.findByStationId(stationId)).thenReturn(resource);
        when(userRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(disasterRepository.findByDisasterId(disasterId)).thenReturn(disaster);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        var response = service.approveRecall("manager1", recallId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(recallStatus.APPROVED, recall.getStatus());
        assertEquals(Status.RECALLED, dispatch.getStatus());
        assertFalse(dispatch.getSimulationActive());

        assertEquals(5, resource.getAvailableVehicle());
        assertEquals(9, resource.getAvailablePersonnel());
        verify(resourceRepository, times(1)).save(resource);

        assertEquals(Admin.AVAILABLE, worker.getWorkerStatus());
        verify(userRepository).save(worker);

        verify(simulationService).stopDispatch(dispatchId);
        verify(simulationService).broadcastStatus(dispatch);

        verify(messagingTemplate).convertAndSendToUser(
                eq("manager1"), eq("/update/mission"), any(recallRequestDto.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("worker1"), eq("/new/mission"), any(recallRequestDto.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("admin1"), eq("/topic/disaster"), any(reportResponse.class));
    }

    @Test
    void duplicateApprovalDoesNotRestoreResourcesTwice() {
        ObjectId recallId = new ObjectId();
        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setStatus(recallStatus.APPROVED);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));

        var response = service.approveRecall("manager1", recallId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(resourceRepository, never()).save(any(resourceEntity.class));
        verify(simulationService, never()).stopDispatch(any());
    }

    @Test
    void backupDispatchRecallRestoresBackupResourcesAndCancelsBackupRecord() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId managerUserId = new ObjectId();
        ObjectId workerId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setDisasterId(disasterId);
        recall.setRequestingWorkerUsername("worker1");
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.POLICE_STATION);

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, disasterId, Status.BACKUP_DISPATCHED);
        dispatch.setWorkerId(workerId);
        dispatch.setAssignedVehicle(1);
        dispatch.setAssignedPersonnel(2);

        backupEntity backup = new backupEntity();
        backup.setDispatchId(dispatchId);
        backup.setReqVehicles(3);
        backup.setReqPersonnel(6);
        backup.setStatus(backupStatus.DISPATCHED);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Beta", Forces.POLICE_STATION);

        resourceEntity resource = new resourceEntity();
        resource.setStationId(stationId);
        resource.setAvailableVehicle(1);
        resource.setAvailablePersonnel(2);

        userEntity worker = createWorker(workerId, stationId, "worker1");

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(backupRepository.findByDispatchId(dispatchId)).thenReturn(backup);
        when(resourceRepository.findByStationId(stationId)).thenReturn(resource);
        when(userRepository.findById(workerId)).thenReturn(Optional.of(worker));

        var response = service.approveRecall("manager1", recallId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.RECALLED, dispatch.getStatus());
        assertEquals(backupStatus.CANCELLED, backup.getStatus());
        verify(backupRepository).save(backup);

        assertEquals(4, resource.getAvailableVehicle());
        assertEquals(8, resource.getAvailablePersonnel());
        verify(resourceRepository).save(resource);
        verify(simulationService).stopDispatch(dispatchId);
    }

    @Test
    void unauthorizedManagerCannotApproveRecall() {
        ObjectId recallId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId managerA = new ObjectId();
        ObjectId managerB = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setStationId(stationId);
        recall.setStatus(recallStatus.PENDING);

        userEntity intruder = createManager(managerB, "intruderManager");
        stationEntity station = createStation(stationId, managerA, "Station Alpha", Forces.FIRE_DEPARTMENT);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("intruderManager")).thenReturn(intruder);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        var response = service.approveRecall("intruderManager", recallId);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(resourceRepository, never()).save(any(resourceEntity.class));
    }

    @Test
    void unauthorizedManagerCannotRejectRecall() {
        ObjectId recallId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId managerA = new ObjectId();
        ObjectId managerB = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setStationId(stationId);
        recall.setStatus(recallStatus.PENDING);

        userEntity intruder = createManager(managerB, "intruderManager");
        stationEntity station = createStation(stationId, managerA, "Station Alpha", Forces.FIRE_DEPARTMENT);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("intruderManager")).thenReturn(intruder);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        var response = service.rejectRecall("intruderManager", recallId);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(recallStatus.PENDING, recall.getStatus());
        verify(recallRequestRepository, never()).save(recall);
    }

    @Test
    void missingRecallRequestReturnsNotFoundForApproveAndReject() {
        ObjectId nonExistentRecallId = new ObjectId();
        when(recallRequestRepository.findById(nonExistentRecallId)).thenReturn(Optional.empty());

        var approveResponse = service.approveRecall("manager1", nonExistentRecallId);
        assertEquals(HttpStatus.NOT_FOUND, approveResponse.getStatusCode());

        var rejectResponse = service.rejectRecall("manager1", nonExistentRecallId);
        assertEquals(HttpStatus.NOT_FOUND, rejectResponse.getStatusCode());
    }

    @Test
    void missingDispatchReturnsNotFoundForRequestAndApprove() {
        ObjectId nonExistentDispatchId = new ObjectId();
        ObjectId workerId = new ObjectId();
        ObjectId stationId = new ObjectId();
        userEntity worker = createWorker(workerId, stationId, "worker1");

        when(userRepository.findByUsername("worker1")).thenReturn(worker);
        when(dispatchRepository.findById(nonExistentDispatchId)).thenReturn(Optional.empty());

        var requestResponse = service.requestRecall("worker1", nonExistentDispatchId, "Missing");
        assertEquals(HttpStatus.NOT_FOUND, requestResponse.getStatusCode());

        ObjectId recallId = new ObjectId();
        ObjectId managerUserId = new ObjectId();
        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(nonExistentDispatchId);
        recall.setStationId(stationId);
        recall.setStatus(recallStatus.PENDING);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        var approveResponse = service.approveRecall("manager1", recallId);
        assertEquals(HttpStatus.NOT_FOUND, approveResponse.getStatusCode());
    }

    @Test
    void nonPendingRecallCannotBeApprovedOrRejectedAgain() {
        ObjectId recallId = new ObjectId();
        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setStatus(recallStatus.REJECTED);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));

        var approveResponse = service.approveRecall("manager1", recallId);
        assertEquals(HttpStatus.BAD_REQUEST, approveResponse.getStatusCode());

        var rejectResponse = service.rejectRecall("manager1", recallId);
        assertEquals(HttpStatus.BAD_REQUEST, rejectResponse.getStatusCode());

        verify(resourceRepository, never()).save(any(resourceEntity.class));
    }

    @Test
    void backupDispatchRecallRejectsMissingBackupRecordWithoutInventingReservation() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId managerUserId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setDisasterId(disasterId);
        recall.setRequestingWorkerUsername("worker1");
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.POLICE_STATION);

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, disasterId, Status.BACKUP_DISPATCHED);
        dispatch.setAssignedVehicle(2);
        dispatch.setAssignedPersonnel(4);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Beta", Forces.POLICE_STATION);

        resourceEntity resource = new resourceEntity();
        resource.setStationId(stationId);
        resource.setAvailableVehicle(1);
        resource.setAvailablePersonnel(2);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(backupRepository.findByDispatchId(dispatchId)).thenReturn(null);

        var response = service.approveRecall("manager1", recallId);

        // Safely rejects without inventing a reservation or altering station resources
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Status.BACKUP_DISPATCHED, dispatch.getStatus());
        assertEquals(1, resource.getAvailableVehicle());
        assertEquals(2, resource.getAvailablePersonnel());
        verify(resourceRepository, never()).save(any(resourceEntity.class));
        verify(simulationService, never()).stopDispatch(any());
    }

    @Test
    void concurrentApprovalLosingAtomicRaceDoesNotRestoreResourcesOrCancelSimulation() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId managerUserId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setDisasterId(disasterId);
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.FIRE_DEPARTMENT);

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        dispatch.setAssignedVehicle(3);
        dispatch.setAssignedPersonnel(6);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);

        resourceEntity resource = new resourceEntity();
        resource.setStationId(stationId);
        resource.setAvailableVehicle(2);
        resource.setAvailablePersonnel(4);

        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(resourceRepository.findByStationId(stationId)).thenReturn(resource);

        // findAndModify returns null when another concurrent thread already updated status from PENDING
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(recallRequestEntity.class)))
                .thenReturn(null);

        var response = service.approveRecall("manager1", recallId);

        // Concurrency loser is rejected immediately
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // Zero side-effects: resources NOT saved, simulation NOT stopped, status NOT changed
        assertEquals(Status.DISPATCHED, dispatch.getStatus());
        assertEquals(2, resource.getAvailableVehicle());
        assertEquals(4, resource.getAvailablePersonnel());
        verify(resourceRepository, never()).save(any(resourceEntity.class));
        verify(simulationService, never()).stopDispatch(any());
        verify(simulationService, never()).broadcastStatus(any());
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void concurrentRejectLosingAtomicRaceDoesNotSendNotifications() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId managerUserId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.FIRE_DEPARTMENT);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);

        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(recallRequestEntity.class)))
                .thenReturn(null);

        var response = service.rejectRecall("manager1", recallId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void atomicFindAndModifyWinningRaceSuccessfullyApprovesAndRestoresResources() {
        ObjectId recallId = new ObjectId();
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();
        ObjectId managerUserId = new ObjectId();
        ObjectId workerId = new ObjectId();

        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(recallId);
        recall.setDispatchId(dispatchId);
        recall.setStationId(stationId);
        recall.setDisasterId(disasterId);
        recall.setRequestingWorkerUsername("worker1");
        recall.setStatus(recallStatus.PENDING);
        recall.setForceType(Forces.FIRE_DEPARTMENT);

        recallRequestEntity approvedRecall = new recallRequestEntity();
        approvedRecall.setRecallId(recallId);
        approvedRecall.setDispatchId(dispatchId);
        approvedRecall.setStationId(stationId);
        approvedRecall.setDisasterId(disasterId);
        approvedRecall.setRequestingWorkerUsername("worker1");
        approvedRecall.setStatus(recallStatus.APPROVED);
        approvedRecall.setForceType(Forces.FIRE_DEPARTMENT);

        dispatchEntity dispatch = createDispatch(dispatchId, stationId, disasterId, Status.DISPATCHED);
        dispatch.setAssignedVehicle(2);
        dispatch.setAssignedPersonnel(5);
        dispatch.setWorkerId(workerId);

        userEntity manager = createManager(managerUserId, "manager1");
        stationEntity station = createStation(stationId, managerUserId, "Station Alpha", Forces.FIRE_DEPARTMENT);

        resourceEntity resource = new resourceEntity();
        resource.setStationId(stationId);
        resource.setAvailableVehicle(3);
        resource.setAvailablePersonnel(7);

        userEntity worker = createWorker(workerId, stationId, "worker1");

        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);

        when(recallRequestRepository.findById(recallId)).thenReturn(Optional.of(recall));
        when(userRepository.findByUsername("manager1")).thenReturn(manager);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(resourceRepository.findByStationId(stationId)).thenReturn(resource);
        when(userRepository.findById(workerId)).thenReturn(Optional.of(worker));

        // findAndModify succeeds atomically
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(recallRequestEntity.class)))
                .thenReturn(approvedRecall);

        var response = service.approveRecall("manager1", recallId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Status.RECALLED, dispatch.getStatus());
        assertEquals(5, resource.getAvailableVehicle());
        assertEquals(12, resource.getAvailablePersonnel());
        verify(resourceRepository).save(resource);
        verify(simulationService).stopDispatch(dispatchId);
        verify(simulationService).broadcastStatus(dispatch);
        verify(messagingTemplate).convertAndSendToUser(eq("manager1"), eq("/update/mission"), any(recallRequestDto.class));
    }

    private static dispatchEntity createDispatch(ObjectId id, ObjectId stationId, ObjectId disasterId, Status status) {
        dispatchEntity dispatch = new dispatchEntity();
        dispatch.setDispatchId(id);
        dispatch.setStationId(stationId);
        dispatch.setDisasterId(disasterId);
        dispatch.setStatus(status);
        dispatch.setArrivedAt(java.time.LocalDateTime.now());
        return dispatch;
    }

    private static userEntity createWorker(ObjectId id, ObjectId stationId, String username) {
        userEntity worker = new userEntity();
        worker.setUserId(id);
        worker.setUsername(username);
        worker.setStationId(stationId);
        worker.setRoles(List.of("WORKER"));
        return worker;
    }

    private static userEntity createManager(ObjectId id, String username) {
        userEntity manager = new userEntity();
        manager.setUserId(id);
        manager.setUsername(username);
        manager.setRoles(List.of("STATION_MANAGER"));
        return manager;
    }

    private static stationEntity createStation(ObjectId id, ObjectId userId, String name, Forces forceType) {
        stationEntity station = new stationEntity();
        station.setStationId(id);
        station.setUserId(userId);
        station.setStationName(name);
        station.setForceType(forceType);
        return station;
    }
}
