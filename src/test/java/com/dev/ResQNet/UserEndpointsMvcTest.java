package com.dev.ResQNet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

@WebMvcTest({publicController.class, UserController.class})
@Import({SecurityConfig.class, TestCacheConfiguration.class})
class UserEndpointsMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private userService userService;

    @MockitoBean
    private userRepo userRepository;

    @MockitoBean
    private MongoDatabaseFactory mongoDatabaseFactory;

    @Test
    void registrationAcceptsValidPayloadAndDelegatesToService() throws Exception {
        doReturn(ResponseEntity.ok("User registered successfully"))
                .when(userService)
                .registration(any(userDTO.class));

        mvc.perform(post("/public/register")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"secret123\",\"username\":\"person\"}"))
                .andExpect(status().isOk());

        verify(userService).registration(any(userDTO.class));
    }

    @Test
    void registrationRejectsInvalidEmailAndShortPassword() throws Exception {
        mvc.perform(post("/public/register")
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"123\",\"username\":\"person\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "person", roles = "USER")
    void currentUserReturnsPublicFieldsAndNeverPassword() throws Exception {
        userEntity user = new userEntity();
        user.setUserId(new ObjectId());
        user.setUsername("person");
        user.setRoles(List.of("USER"));
        user.setPassword("$2a$10$stored-hash-must-not-appear");

        when(userRepository.findByUsername("person")).thenReturn(user);

        mvc.perform(get("/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.username").value("person"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userRepository).findByUsername("person");
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository);
    }
}
