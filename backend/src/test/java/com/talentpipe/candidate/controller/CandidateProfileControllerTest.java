package com.talentpipe.candidate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentpipe.candidate.dto.CandidateCvResponse;
import com.talentpipe.candidate.dto.MyCandidateProfileResponse;
import com.talentpipe.candidate.service.CandidateCvService;
import com.talentpipe.candidate.service.CandidateProfileService;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CandidateProfileController.class)
@Import(WebMvcSecurityTestConfig.class)
class CandidateProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandidateProfileService profileService;

    @MockitoBean
    private CandidateCvService cvService;

    private UUID candidateId;
    private MyCandidateProfileResponse mockResponse;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();
        mockResponse = new MyCandidateProfileResponse(
                candidateId,
                "Kasun Perera",
                "kasun@example.com",
                "+94 77 123 4567",
                "200012345678",
                "/api/v1/public/media/candidate/" + candidateId + "/photos/avatar.png",
                true,
                new CandidateCvResponse("resume.pdf", 1024L, "/api/v1/candidates/me/cv/download"));
    }

    @Test
    void getMyProfileRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/candidates/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfileRejectsForbiddenRole() throws Exception {
        mockMvc.perform(get("/api/v1/candidates/me")
                        .with(WebMvcSecurityTestConfig.asUser(candidateId, UUID.randomUUID(), "COMPANY_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyProfileReturnsCandidateProfile() throws Exception {
        when(profileService.getProfile(candidateId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/candidates/me")
                        .with(WebMvcSecurityTestConfig.asUser(candidateId, null, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidateId.toString()))
                .andExpect(jsonPath("$.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.email").value("kasun@example.com"))
                .andExpect(jsonPath("$.inTalentPool").value(true))
                .andExpect(jsonPath("$.cv.fileName").value("resume.pdf"));
    }

    @Test
    void updateMyProfileSucceedsWithMultipart() throws Exception {
        when(profileService.updateProfile(eq(candidateId), any())).thenReturn(mockResponse);

        MockMultipartFile photo = new MockMultipartFile(
                "photo", "photo.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile cv = new MockMultipartFile(
                "cv", "resume.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/candidates/me")
                        .file(photo)
                        .file(cv)
                        .param("fullName", "Kasun Perera")
                        .param("email", "kasun@example.com")
                        .param("identityCardNumber", "200012345678")
                        .param("phone", "+94 77 123 4567")
                        .param("addToTalentPool", "true")
                        .with(WebMvcSecurityTestConfig.asUser(candidateId, null, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.inTalentPool").value(true));
    }

    @Test
    void downloadMyCvStreamsFileAttachment() throws Exception {
        byte[] dummyCv = new byte[]{0x25, 0x50, 0x44, 0x46, 1, 2, 3};
        when(cvService.loadActiveCvResource(candidateId))
                .thenReturn(new CandidateCvService.CvDownloadPayload("resume.pdf", "application/pdf", dummyCv));

        mockMvc.perform(get("/api/v1/candidates/me/cv/download")
                        .with(WebMvcSecurityTestConfig.asUser(candidateId, null, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("resume.pdf")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(dummyCv));
    }
}
