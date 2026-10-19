package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(stationController.class)
@Import({SecurityConfig.class, TestCacheConfiguration.class})
class StationEndpointsMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private stationService stationService;

    @MockitoBean
    private stationRepo stationRepository;

    @MockitoBean
    private userRepo userRepository;

    @MockitoBean
    private dispatchRepo dispatchRepository;

    @MockitoBean
    private disasterRepo disasterRepository;

    @MockitoBean
    private MongoDatabaseFactory mongoDatabaseFactory;

    @Test
    @WithMockUser(username = "manager", roles = "STATION_MANAGER")
    void fakeEndpointDelegatesDispatchIdAndManagerName() throws Exception {
        ObjectId id = new ObjectId();

        doReturn(ResponseEntity.ok("Dispatch marked as fake successfully."))
                .when(stationService)
                .fakeMission(id, "manager");

        mvc.perform(get("/station/{dispatchId}/fake", id.toHexString()))
                .andExpect(status().isOk());

        verify(stationService).fakeMission(id, "manager");
        verifyNoInteractions(dispatchRepository, disasterRepository);
    }

    @Test
    @WithMockUser(username = "manager", roles = "STATION_MANAGER")
    void rejectEndpointRecordsReporterAndRequestsReplacementMission() throws Exception {
        ObjectId dispatchId = new ObjectId();
        ObjectId stationId = new ObjectId();
        ObjectId disasterId = new ObjectId();

        userEntity manager = manager(stationId);
        dispatchEntity dispatch = dispatch(dispatchId, stationId, disasterId);

        stationEntity station = new stationEntity();
        station.setStationId(stationId);
        station.setForceType(Forces.SDRF);

        disasterEntity disaster = new disasterEntity();
        disaster.setDisasterId(disasterId);

        when(userRepository.findByUsername("manager")).thenReturn(manager);
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(disasterRepository.findByDisasterId(disasterId)).thenReturn(disaster);

        mvc.perform(get("/station/{dispatchId}/reject", dispatchId.toHexString()))
                .andExpect(status().isOk());

        assertTrue(disaster.getRejectedStations().contains(stationId));
        verify(userRepository).save(manager);
        verify(disasterRepository).save(disaster);
        verify(stationService).newMission(dispatchId, Forces.SDRF);
    }

    @Test
    @WithMockUser(username = "manager", roles = "STATION_MANAGER")
    void rejectForAnotherStationMustNotMutateManagerStats() throws Exception {
        ObjectId dispatchId = new ObjectId();
        ObjectId managerStationId = new ObjectId();
        ObjectId assignedStationId = new ObjectId();

        userEntity manager = manager(managerStationId);

        when(userRepository.findByUsername("manager")).thenReturn(manager);
        when(dispatchRepository.findById(dispatchId))
                .thenReturn(Optional.of(dispatch(dispatchId, assignedStationId, new ObjectId())));

        mvc.perform(get("/station/{dispatchId}/reject", dispatchId.toHexString()))
                .andExpect(status().isForbidden());

        assertEquals(0, manager.getStats().getFalseReports());
        assertEquals(0, manager.getStats().getTotalAssigned());

        verify(userRepository, never()).save(manager);
        verify(stationRepository, never()).findById(assignedStationId);
    }

    @Test
    @WithMockUser(username = "worker", roles = "WORKER")
    void stationRoutesRequireStationManagerRole() throws Exception {
        mvc.perform(get("/station/{dispatchId}/fake", new ObjectId().toHexString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(stationService, stationRepository, userRepository,
                dispatchRepository, disasterRepository);
    }

    private static userEntity manager(ObjectId stationId) {
        userEntity manager = new userEntity();
        manager.setUserId(new ObjectId());
        manager.setUsername("manager");
        manager.setStationId(stationId);
        manager.setStats(new Stats(0, 0, 0, 0));
        return manager;
    }

    private static dispatchEntity dispatch(ObjectId id, ObjectId stationId, ObjectId disasterId) {
        dispatchEntity dispatch = new dispatchEntity();
        dispatch.setDispatchId(id);
        dispatch.setStationId(stationId);
        dispatch.setDisasterId(disasterId);
        return dispatch;
    }
}
