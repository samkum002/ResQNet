package com.dev.ResQNet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(backupController.class)
@Import({
        SecurityConfig.class,
        DispatchEndpointsMvcTest.TestCacheConfiguration.class
})
class DispatchEndpointsMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private stationService stationService;

    @MockitoBean
    private MongoDatabaseFactory mongoDatabaseFactory;

    @TestConfiguration
    static class TestCacheConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "WORKER")
    void backupRequestPassesAuthenticatedWorkerAndDtoToService() throws Exception {
        ObjectId id = new ObjectId();

        doReturn(ResponseEntity.ok("Backup request sent successfully."))
                .when(stationService)
                .requestBackup(eq("worker-one"), any(backupDTO.class), eq(id));

        mvc.perform(post("/dispatch/backup/{dispatchId}/request", id.toHexString())
                        .contentType("application/json")
                        .content("{\"reqVehicles\":2,\"reqPersonnel\":5}"))
                .andExpect(status().isOk());

        verify(stationService).requestBackup(eq("worker-one"), any(backupDTO.class), eq(id));
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "USER")
    void dispatchRoutesRejectNonWorkerRole() throws Exception {
        ObjectId id = new ObjectId();

        mvc.perform(post("/dispatch/start/{dispatchId}", id.toHexString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(stationService);
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "WORKER")
    void startEndpointUsesAuthenticatedWorkerAndDoesNotRequireBody() throws Exception {
        ObjectId id = new ObjectId();

        doReturn(ResponseEntity.ok("Dispatch simulation started."))
                .when(stationService)
                .startDispatch(id, "worker-one");

        mvc.perform(post("/dispatch/start/{dispatchId}", id.toHexString()))
                .andExpect(status().isOk());

        verify(stationService).startDispatch(id, "worker-one");
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "WORKER")
    void startEndpointReturnsServiceNotFoundResponseForUnknownDispatch() throws Exception {
        ObjectId id = new ObjectId();

        doReturn(ResponseEntity.status(404).body("Dispatch not found."))
                .when(stationService)
                .startDispatch(id, "worker-one");

        mvc.perform(post("/dispatch/start/{dispatchId}", id.toHexString()))
                .andExpect(status().isNotFound());

        verify(stationService).startDispatch(id, "worker-one");
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "WORKER")
    void completeAndFalseEndpointsDelegateWithAuthenticatedWorker() throws Exception {
        ObjectId completeId = new ObjectId();
        ObjectId falseId = new ObjectId();

        doReturn(ResponseEntity.ok("Dispatch completed successfully."))
                .when(stationService)
                .completeDispatch(completeId, "worker-one");

        doReturn(ResponseEntity.ok("Dispatch marked as fake successfully."))
                .when(stationService)
                .falseDispatch(falseId, "worker-one");

        mvc.perform(patch("/dispatch/complete/{dispatchId}", completeId.toHexString()))
                .andExpect(status().isOk());

        mvc.perform(patch("/dispatch/false/{dispatchId}", falseId.toHexString()))
                .andExpect(status().isOk());

        verify(stationService).completeDispatch(completeId, "worker-one");
        verify(stationService).falseDispatch(falseId, "worker-one");
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "WORKER")
    void malformedDispatchIdIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(post("/dispatch/start/not-an-object-id"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stationService);
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "USER")
    void recallEndpointRejectsNonWorkerRole() throws Exception {
        ObjectId id = new ObjectId();

        mvc.perform(post("/dispatch/recall/{dispatchId}", id.toHexString())
                        .contentType("application/json")
                        .content("{\"reason\":\"Recall test\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(stationService);
    }

    @Test
    @WithMockUser(username = "worker-one", roles = "WORKER")
    void recallEndpointPassesAuthenticatedWorkerAndReasonToService() throws Exception {
        ObjectId id = new ObjectId();

        doReturn(ResponseEntity.ok("Recall request submitted."))
                .when(stationService)
                .requestRecall(eq("worker-one"), eq(id), eq("Assistance no longer needed"));

        mvc.perform(post("/dispatch/recall/{dispatchId}", id.toHexString())
                        .contentType("application/json")
                        .content("{\"reason\":\"Assistance no longer needed\"}"))
                .andExpect(status().isOk());

        verify(stationService).requestRecall(eq("worker-one"), eq(id), eq("Assistance no longer needed"));
    }
}