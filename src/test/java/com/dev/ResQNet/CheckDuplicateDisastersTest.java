package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class CheckDuplicateDisastersTest {

    private adminDashboardServiceImpl dashboardService;
    private disasterRepo disasterRepo;
    private userRepo userRepo;
    private MongoTemplate mongoTemplate;
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        dashboardService = new adminDashboardServiceImpl();
        disasterRepo = mock(disasterRepo.class);
        userRepo = mock(userRepo.class);
        mongoTemplate = mock(MongoTemplate.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        ReflectionTestUtils.setField(dashboardService, "DisasterRepo", disasterRepo);
        ReflectionTestUtils.setField(dashboardService, "userrepo", userRepo);
        ReflectionTestUtils.setField(dashboardService, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(dashboardService, "template", messagingTemplate);
    }

    @Test
    void matchesGenuineDuplicateWithin500MetersAndLinksToOriginal() {
        ObjectId originalId = new ObjectId();
        ObjectId newId = new ObjectId();
        ObjectId reporterId = new ObjectId();
        ObjectId adminId = new ObjectId();

        disasterEntity original = new disasterEntity();
        original.setDisasterId(originalId);
        original.setUserReport("Fire");
        original.setStatus(Status.REPORTED);
        original.setReportCount(1);
        original.setAiConfidence(80);
        original.setUserId(reporterId);
        original.setAssignedAdminId(adminId);
        original.setLocation(new GeoJsonPoint(73.8567, 18.5204));

        disasterEntity newReport = new disasterEntity();
        newReport.setDisasterId(newId);
        newReport.setUserReport("fire");
        newReport.setUserId(reporterId);
        newReport.setStatus(Status.REPORTED);
        newReport.setLocation(new GeoJsonPoint(73.8569, 18.5206));

        userEntity reporter = new userEntity();
        reporter.setUserId(reporterId);
        reporter.setUsername("reporter1");
        reporter.setTrustScore(70);

        userEntity admin = new userEntity();
        admin.setUserId(adminId);
        admin.setUsername("admin1");

        when(disasterRepo.findByDisasterId(newId)).thenReturn(newReport);
        when(disasterRepo.findByDisasterId(originalId)).thenReturn(original);
        when(userRepo.findByUserId(reporterId)).thenReturn(reporter);
        when(userRepo.findByUserId(adminId)).thenReturn(admin);
        when(mongoTemplate.find(any(Query.class), eq(disasterEntity.class))).thenReturn(List.of(original));

        boolean isDup = dashboardService.checkDuplicateDisasters(18.5206, 73.8569, "Maharashtra", newId);

        assertTrue(isDup);
        assertEquals(Status.ALREADY_PRESENT, newReport.getStatus());
        assertEquals(originalId, newReport.getLinkedDisasterId());
        assertEquals(2, original.getReportCount());
        verify(disasterRepo, org.mockito.Mockito.atLeastOnce()).save(original);
        verify(disasterRepo).save(newReport);
        verify(messagingTemplate).convertAndSend(eq("/topic/disaster"), any(duplicateDisasterDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq("reporter1"), eq("/queue/report"), any(reportResponse.class));
    }

    @Test
    void returnsFalseWhenNoMatchingIncidentNearby() {
        ObjectId newId = new ObjectId();
        disasterEntity newReport = new disasterEntity();
        newReport.setDisasterId(newId);
        newReport.setUserReport("Flood");

        when(disasterRepo.findByDisasterId(newId)).thenReturn(newReport);
        when(mongoTemplate.find(any(Query.class), eq(disasterEntity.class))).thenReturn(List.of());

        boolean isDup = dashboardService.checkDuplicateDisasters(18.5206, 73.8569, "Maharashtra", newId);

        assertFalse(isDup);
    }
}
