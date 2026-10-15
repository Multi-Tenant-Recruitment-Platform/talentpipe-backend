package com.talentpipe.candidate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.dto.CandidateRegisterRequest;
import com.talentpipe.candidate.service.CandidateService;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicCandidateController.class)
@Import(WebMvcSecurityTestConfig.class)
class PublicCandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CandidateService candidateService;

    @Test
    void register_validPayload_returnsCreatedWithProfile() throws Exception {
        CandidateRegisterRequest request = new CandidateRegisterRequest(
                "Kasun Perera",
                "200012345678",
                "Colombo",
                "+94 77 123 4567",
                "kasun@example.com",
                "Password123!");

        UUID id = UUID.randomUUID();
        CandidateProfile profile = new CandidateProfile(
                id,
                "kasun@example.com",
                "Kasun",
                "Perera",
                "PENDING_VERIFICATION",
                Instant.now());

        when(candidateService.register(any(CandidateRegisterRequest.class))).thenReturn(profile);

        mockMvc.perform(post("/api/v1/public/candidates/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("kasun@example.com"))
                .andExpect(jsonPath("$.firstName").value("Kasun"))
                .andExpect(jsonPath("$.lastName").value("Perera"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    void register_invalidPayload_returnsBadRequest() throws Exception {
        CandidateRegisterRequest invalid = new CandidateRegisterRequest(
                "",
                "invalid-nic",
                "",
                "",
                "not-an-email",
                "short");

        mockMvc.perform(post("/api/v1/public/candidates/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}
