package com.dev.ResQNet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/disaster")
public class disasterController {

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private adminDashboardService dashboardService;

    @Autowired
    private disasterRepo disasterrepo;

    @Autowired
    private SimpMessagingTemplate template;

    @Autowired
    private aiAnalyzerService aiAnalyzerservice;

    @Autowired
    private disasterService disasterservice;

    @Autowired
    private userRepo userrepo;

    @Autowired
    private ReverseGeocodingService reverseGeocoding;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/my-reports")
    public ResponseEntity<List<userReportDto>> getMyReports() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if (user == null) {
            return ResponseEntity.badRequest().build();
        }
        List<disasterEntity> userDisasters = disasterrepo.findByUserId(user.getUserId());
        List<userReportDto> dtos = userDisasters.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(d -> new userReportDto(
                        d.getDisasterId() != null ? d.getDisasterId().toHexString() : null,
                        d.getStatus() == Status.ALREADY_PRESENT ? "Disaster is already reported." : "Reported to Command Center",
                        d.getStatus(),
                        d.getUserReport() != null ? d.getUserReport() : "Incident",
                        d.getLocation() != null ? d.getLocation().getY() : null,
                        d.getLocation() != null ? d.getLocation().getX() : null,
                        d.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/{disasterId}/update")
    public ResponseEntity<?> updateDisaster(@PathVariable ObjectId disasterId, @RequestBody(required=false) disasterDto dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        disasterEntity disaster = disasterrepo.findByDisasterId(disasterId);
        if (user == null || disaster == null) {
            return ResponseEntity.notFound().build();
        }
        if (dto != null) {
            if (dto.getSeverity() != null) {
                disaster.setSeverity(dto.getSeverity());
            }
            if (dto.getForces() != null) {
                disaster.setForces(dto.getForces());
            }
            if (dto.getDisasterType() != null) {
                disaster.setDisasterType(dto.getDisasterType());
            }
            if (dto.getSuspicious() != null) {
                disaster.setSuspicious(dto.getSuspicious());
            }
            if (dto.getUserReport() != null && !dto.getUserReport().isBlank()) {
                disaster.setUserReport(dto.getUserReport().trim());
            }
            disasterrepo.save(disaster);
        }
        disasterDto responseDto = new disasterDto();
        responseDto.setDisasterId(disaster.getDisasterId().toHexString());
        responseDto.setSeverity(disaster.getSeverity());
        responseDto.setForces(disaster.getForces());
        responseDto.setDisasterType(disaster.getDisasterType());
        responseDto.setSuspicious(disaster.getSuspicious());
        responseDto.setUserReport(disaster.getUserReport());
        responseDto.setStatus(disaster.getStatus());
        responseDto.setAiStatus(disaster.getAiStatus());
        responseDto.setAiConfidence(disaster.getAiConfidence());
        responseDto.setFinalConfidence(disaster.getFinalConfidence());
        responseDto.setReportCount(disaster.getReportCount());
        responseDto.setState(disaster.getState());
        responseDto.setImage(disaster.getImage() != null ? disaster.getImage().toHexString() : null);
        return ResponseEntity.ok(responseDto);
    }

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    private boolean isSupportedImageSignature(byte[] header, int bytesRead) {
        if (bytesRead < 3) return false;
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (bytesRead >= 4 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // GIF: GIF87a or GIF89a
        if (bytesRead >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8'
                && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
            return true;
        }
        // WEBP: RIFF....WEBP
        if (bytesRead >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return true;
        }
        return false;
    }

    private String detectImageContentType(byte[] header, int bytesRead, String fallback) {
        if (bytesRead >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytesRead >= 4 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return "image/png";
        }
        if (bytesRead >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return "image/gif";
        }
        if (bytesRead >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return fallback != null && !fallback.isBlank() ? fallback : "image/jpeg";
    }

    @PostMapping("/report")
    public ResponseEntity<?> imageUpload(
            @RequestParam("image") MultipartFile image,
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @Valid @NotBlank(message="Please Enter valid Disaster Cause.")
            @Pattern(regexp="^[A-Za-z_ ]+$", message="Please Enter valid Disaster Cause.")
            @RequestParam("userReport") String userReport) throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if (user == null) {
            return ResponseEntity.badRequest().body("user doesn't exist");
        }

        // 1. Authoritative Backend Validation for Image Evidence
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body("Image file cannot be empty.");
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            return ResponseEntity.status(413).body("Image file exceeds the maximum allowed limit of 10MB.");
        }

        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = image.getInputStream()) {
            bytesRead = is.read(header);
        }
        if (!isSupportedImageSignature(header, bytesRead)) {
            return ResponseEntity.badRequest().body("Unsupported or invalid image file. Allowed formats: JPEG, PNG, WEBP, GIF.");
        }

        String resolvedContentType = detectImageContentType(header, bytesRead, image.getContentType());
        String filename = image.getOriginalFilename() != null && !image.getOriginalFilename().isBlank()
                ? image.getOriginalFilename()
                : "disaster_evidence.jpg";

        // 2. Stream and Persist Image in MongoDB GridFS
        ObjectId image_store;
        try (InputStream uploadStream = image.getInputStream()) {
            image_store = gridFsTemplate.store(uploadStream, filename, resolvedContentType);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body("Failed to persist incident image.");
        }

        // 3. Create Disaster Report Record
        ObjectId userId = user.getUserId();
        disasterEntity disaster = new disasterEntity();
        disaster.setUserReport(userReport.trim());
        disaster.setUserId(userId);
        disaster.setStatus(Status.REPORTED);
        disaster.setCreatedAt(LocalDateTime.now());
        disaster.setReportCount(1);
        disaster.setRetryCount(0);
        disaster.setAiConfidence(0);
        disaster.setFinalConfidence(0.0);
        disaster.setSuspicious(false);
        disaster.setLocation(new GeoJsonPoint(longitude, latitude));
        disaster.setAssignmentStatus(Assignment.CREATED);
        disaster.setImage(image_store);

        try {
            disasterrepo.save(disaster);
        } catch (Exception ex) {
            // Avoid orphaned GridFS file if disaster record save fails
            gridFsTemplate.delete(org.springframework.data.mongodb.core.query.Query.query(
                    org.springframework.data.mongodb.core.query.Criteria.where("_id").is(image_store)));
            return ResponseEntity.internalServerError().body("Failed to create disaster report.");
        }

        reverseGeocoding.findAndSaveState(disaster, latitude, longitude);
        ObjectId disasterId = disaster.getDisasterId();

        // 4. Duplicate detection occurs before AI scheduling
        boolean isDuplicate = dashboardService.checkDuplicateDisasters(latitude, longitude, disaster.getState(), disasterId);
        if (isDuplicate) {
            disasterEntity duplicateDisaster = disasterrepo.findByDisasterId(disasterId);
            Status s = duplicateDisaster != null ? duplicateDisaster.getStatus() : Status.ALREADY_PRESENT;
            reportResponse dupMsg = new reportResponse(disasterId.toHexString(), "Disaster is already reported.", s);
            return ResponseEntity.ok(dupMsg);
        }

        // 5. Downstream AI Processing: verify and pass stored image data from GridFS
        byte[] completeStoredBytes = null;
        try {
            com.mongodb.client.gridfs.model.GridFSFile storedGfs = gridFsTemplate.findOne(
                    org.springframework.data.mongodb.core.query.Query.query(
                            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(image_store)));
            if (storedGfs != null) {
                org.springframework.data.mongodb.gridfs.GridFsResource res = gridFsTemplate.getResource(storedGfs);
                completeStoredBytes = res.getInputStream().readAllBytes();
            }
        } catch (Exception ex) {
            // Keep report intact; AI service retry flow will handle any transient read error
        }

        if (completeStoredBytes != null) {
            try {
                aiAnalyzerservice.aiAnalyzer(completeStoredBytes, disasterId, resolvedContentType);
                disaster.setAiStatus(AI.PROCESSING);
                disasterrepo.save(disaster);
                reportResponse aiResponse = new reportResponse(disaster.getDisasterId().toHexString(), "AI analysis", disaster.getStatus());
                template.convertAndSendToUser(name, "/queue/report", aiResponse);
            } catch (Exception ex) {
                // Keep report intact; background worker/scheduler handles AI reprocessing
            }
        }

        reportResponse reportedResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Reported Successfully", disaster.getStatus());
        template.convertAndSendToUser(name, "/queue/report", reportedResponse);
        return ResponseEntity.ok(reportedResponse);
    }
}
