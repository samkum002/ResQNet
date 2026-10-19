package com.dev.ResQNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserRegistrationServiceTest {

    private userRepo userRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private userServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(userRepo.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new userServiceImpl();
        ReflectionTestUtils.setField(service, "repo", userRepository);
        ReflectionTestUtils.setField(service, "encoder", passwordEncoder);
    }

    @Test
    void registrationStoresEncodedPasswordAndDefaultUserRole() {
        userDTO dto = new userDTO("new@example.com", "secret123", "new-user");
        when(userRepository.findByUsername("new-user")).thenReturn(null);
        when(userRepository.save(any(userEntity.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.registration(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        org.mockito.ArgumentCaptor<userEntity> saved =
                org.mockito.ArgumentCaptor.forClass(userEntity.class);
        verify(userRepository).save(saved.capture());
        assertNotEquals("secret123", saved.getValue().getPassword());
        assertTrue(passwordEncoder.matches("secret123", saved.getValue().getPassword()));
        assertEquals(java.util.List.of("USER"), saved.getValue().getRoles());
    }

    @Test
    void registrationRejectsDuplicateUsernameWithoutSaving() {
        when(userRepository.findByUsername("taken")).thenReturn(new userEntity());

        var response = service.registration(
                new userDTO("new@example.com", "secret123", "taken"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(userRepository, never()).save(any(userEntity.class));
    }
}
