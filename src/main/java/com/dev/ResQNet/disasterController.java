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

    @PostMapping("/report")
    public ResponseEntity<?> imageUpload(
            @RequestParam("image") MultipartFile image,
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @Valid @NotBlank(message="Please Enter valid Disaster Cause in one word only.")
            @Pattern(regexp="^[A-Za-z]+$")
            @RequestParam("userReport") String userReport) throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if (user == null) {
            return ResponseEntity.badRequest().body("user doesn't exist");
        }
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
        byte[] image_bytes = image.getBytes();
        InputStream image_to_stream = new ByteArrayInputStream(image_bytes);
        String content = image.getContentType();
        ObjectId image_store = gridFsTemplate.store(image_to_stream, image.getOriginalFilename(), content);
        disaster.setImage(image_store);
        disasterrepo.save(disaster);
        reverseGeocoding.findAndSaveState(disaster, latitude, longitude);
        ObjectId disasterId = disaster.getDisasterId();

        // Section 8: Duplicate detection occurs before AI scheduling
        boolean isDuplicate = dashboardService.checkDuplicateDisasters(latitude, longitude, disaster.getState(), disasterId);
        if (isDuplicate) {
            disasterEntity duplicateDisaster = disasterrepo.findByDisasterId(disasterId);
            Status s = duplicateDisaster != null ? duplicateDisaster.getStatus() : Status.ALREADY_PRESENT;
            reportResponse dupMsg = new reportResponse(disasterId.toHexString(), "Disaster is already reported.", s);
            return ResponseEntity.ok(dupMsg);
        }

        // Only start AI analysis for non-duplicates
        aiAnalyzerservice.aiAnalyzer(image_bytes, disasterId, content);
        disaster.setAiStatus(AI.PROCESSING);
        disasterrepo.save(disaster);
        reportResponse aiResponse = new reportResponse(disaster.getDisasterId().toHexString(), "AI analysis", disaster.getStatus());
        template.convertAndSendToUser(name, "/queue/report", aiResponse);

        reportResponse reportedResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Reported Successfully", disaster.getStatus());
        template.convertAndSendToUser(name, "/queue/report", reportedResponse);
        return ResponseEntity.ok(reportedResponse);
    }
}
