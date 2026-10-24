package com.dev.ResQNet;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private userRepo repo;

    @Autowired
    private stationRepo stationRepository;

    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> currentUser(Authentication authentication) {
        String username = authentication.getName();
        userEntity user = repo.findByUsername(username);

        UserInfoDto dto = new UserInfoDto();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setRoles(user.getRoles());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/stations")
    public ResponseEntity<List<stationMapDto>> getStations() {
        List<stationEntity> stations = stationRepository.findAll();
        List<stationMapDto> dtos = stations.stream()
                .filter(s -> s.getLocation() != null)
                .map(s -> new stationMapDto(
                        s.getStationId() != null ? s.getStationId().toHexString() : null,
                        s.getStationName(),
                        s.getForceType() != null ? s.getForceType().name() : "STATION",
                        s.getState(),
                        s.getLocation().getY(), // latitude
                        s.getLocation().getX()  // longitude
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
