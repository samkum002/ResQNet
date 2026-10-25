package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mongodb.client.gridfs.model.GridFSFile;

@WebMvcTest(disasterController.class)
@Import({SecurityConfig.class, TestCacheConfiguration.class, GlobalExceptionHandler.class})
class DisasterReportImageUploadTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MongoDatabaseFactory mongoDatabaseFactory;

    @MockitoBean
    private GridFsTemplate gridFsTemplate;

    @MockitoBean
    private adminDashboardService dashboardService;

    @MockitoBean
    private disasterRepo disasterrepo;

    @MockitoBean
    private disasterService disasterservice;

    @MockitoBean
    private SimpMessagingTemplate template;

    @MockitoBean
    private aiAnalyzerService aiAnalyzerservice;

    @MockitoBean
    private userRepo userrepo;

    @MockitoBean
    private ReverseGeocodingService reverseGeocoding;

    private userEntity testUser;

    // Standard valid JPEG header bytes: FF D8 FF E0
    private static byte[] createValidJpegBytes(int totalSize) {
        byte[] bytes = new byte[Math.max(16, totalSize)];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        for (int i = 4; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return bytes;
    }

    // Standard valid PNG header bytes: 89 50 4E 47 0D 0A 1A 0A
    private static byte[] createValidPngBytes(int totalSize) {
        byte[] bytes = new byte[Math.max(16, totalSize)];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4E;
        bytes[3] = 0x47;
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return bytes;
    }

    @BeforeEach
    void setUp() {
        testUser = new userEntity();
        testUser.setUserId(new ObjectId());
        testUser.setUsername("testuser");
        when(userrepo.findByUsername("testuser")).thenReturn(testUser);
        when(disasterrepo.save(any(disasterEntity.class))).thenAnswer(invocation -> {
            disasterEntity entity = invocation.getArgument(0);
            if (entity.getDisasterId() == null) {
                entity.setDisasterId(new ObjectId());
            }
            return entity;
        });
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadValidSmallJpegSucceedsAndStoresInGridFs() throws Exception {
        byte[] jpegBytes = createValidJpegBytes(2048);
        MockMultipartFile file = new MockMultipartFile(
                "image", "fire_photo.jpg", "image/jpeg", jpegBytes);

        ObjectId imageStoreId = new ObjectId();
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString())).thenReturn(imageStoreId);

        GridFSFile mockGfs = mock(GridFSFile.class);
        GridFsResource mockRes = mock(GridFsResource.class);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(mockGfs);
        when(gridFsTemplate.getResource(mockGfs)).thenReturn(mockRes);
        when(mockRes.getInputStream()).thenReturn(new ByteArrayInputStream(jpegBytes));
        when(dashboardService.checkDuplicateDisasters(anyDouble(), anyDouble(), any(), any())).thenReturn(false);

        mvc.perform(multipart("/disaster/report")
                        .file(file)
                        .param("latitude", "18.5204")
                        .param("longitude", "73.8567")
                        .param("userReport", "FIRE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Reported Successfully"));

        verify(gridFsTemplate).store(any(InputStream.class), eq("fire_photo.jpg"), eq("image/jpeg"));
        verify(disasterrepo, org.mockito.Mockito.atLeastOnce()).save(any(disasterEntity.class));
        verify(aiAnalyzerservice).aiAnalyzer(any(byte[].class), any(ObjectId.class), eq("image/jpeg"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadWithMultiWordQuickTagsSucceeds() throws Exception {
        String[] multiWordTags = {"BUILDING COLLAPSE", "ROAD ACCIDENT", "ELECTRICAL HAZARD", "LANDSLIDE", "EARTHQUAKE"};

        for (String tag : multiWordTags) {
            byte[] pngBytes = createValidPngBytes(1024);
            MockMultipartFile file = new MockMultipartFile(
                    "image", "evidence.png", "image/png", pngBytes);

            ObjectId imageStoreId = new ObjectId();
            when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString())).thenReturn(imageStoreId);

            GridFSFile mockGfs = mock(GridFSFile.class);
            GridFsResource mockRes = mock(GridFsResource.class);
            when(gridFsTemplate.findOne(any(Query.class))).thenReturn(mockGfs);
            when(gridFsTemplate.getResource(mockGfs)).thenReturn(mockRes);
            when(mockRes.getInputStream()).thenReturn(new ByteArrayInputStream(pngBytes));

            mvc.perform(multipart("/disaster/report")
                            .file(file)
                            .param("latitude", "19.0760")
                            .param("longitude", "72.8777")
                            .param("userReport", tag)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.msg").value("Reported Successfully"));
        }
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadLargeValidImageWithinLimitSucceedsAndMatchesBytes() throws Exception {
        // 4 MB valid JPEG image
        byte[] largeImageBytes = createValidJpegBytes(4 * 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "image", "large_evidence.jpg", "image/jpeg", largeImageBytes);

        ObjectId imageStoreId = new ObjectId();
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString())).thenReturn(imageStoreId);

        GridFSFile mockGfs = mock(GridFSFile.class);
        GridFsResource mockRes = mock(GridFsResource.class);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(mockGfs);
        when(gridFsTemplate.getResource(mockGfs)).thenReturn(mockRes);
        when(mockRes.getInputStream()).thenReturn(new ByteArrayInputStream(largeImageBytes));

        mvc.perform(multipart("/disaster/report")
                        .file(file)
                        .param("latitude", "28.6139")
                        .param("longitude", "77.2090")
                        .param("userReport", "FIRE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Reported Successfully"));

        ArgumentCaptor<byte[]> aiBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(aiAnalyzerservice).aiAnalyzer(aiBytesCaptor.capture(), any(ObjectId.class), eq("image/jpeg"));
        assertArrayEquals(largeImageBytes, aiBytesCaptor.getValue());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadEmptyImageIsRejected() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        mvc.perform(multipart("/disaster/report")
                        .file(emptyFile)
                        .param("latitude", "28.6139")
                        .param("longitude", "77.2090")
                        .param("userReport", "FIRE")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadOversizedImageExceeding10MBIsRejected() throws Exception {
        // 11 MB file
        byte[] oversizedBytes = createValidJpegBytes(11 * 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "image", "huge.jpg", "image/jpeg", oversizedBytes);

        mvc.perform(multipart("/disaster/report")
                        .file(file)
                        .param("latitude", "28.6139")
                        .param("longitude", "77.2090")
                        .param("userReport", "FIRE")
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadInvalidNonImageFileIsRejected() throws Exception {
        // Plain text file disguised as .jpg
        byte[] textBytes = "This is not an image file content!".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "image", "fake.jpg", "image/jpeg", textBytes);

        mvc.perform(multipart("/disaster/report")
                        .file(file)
                        .param("latitude", "28.6139")
                        .param("longitude", "77.2090")
                        .param("userReport", "FIRE")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void downstreamAiFailureDoesNotDestroyPersistedDisasterReport() throws Exception {
        byte[] jpegBytes = createValidJpegBytes(1024);
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", jpegBytes);

        ObjectId imageStoreId = new ObjectId();
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString())).thenReturn(imageStoreId);

        GridFSFile mockGfs = mock(GridFSFile.class);
        GridFsResource mockRes = mock(GridFsResource.class);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(mockGfs);
        when(gridFsTemplate.getResource(mockGfs)).thenReturn(mockRes);
        when(mockRes.getInputStream()).thenReturn(new ByteArrayInputStream(jpegBytes));

        // Simulate AI service failure
        doThrow(new RuntimeException("Simulated AI service timeout"))
                .when(aiAnalyzerservice).aiAnalyzer(any(byte[].class), any(ObjectId.class), anyString());

        mvc.perform(multipart("/disaster/report")
                        .file(file)
                        .param("latitude", "18.5204")
                        .param("longitude", "73.8567")
                        .param("userReport", "FIRE")
                        .with(csrf()))
                .andExpect(status().isOk());

        // Report was successfully created and persisted before AI was scheduled
        verify(disasterrepo, org.mockito.Mockito.atLeastOnce()).save(any(disasterEntity.class));
    }
}
