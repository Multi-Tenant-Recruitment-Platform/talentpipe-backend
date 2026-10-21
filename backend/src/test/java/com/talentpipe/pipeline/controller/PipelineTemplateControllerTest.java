package com.talentpipe.pipeline.controller;

import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.pipeline.PipelineTemplateFixtures;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.service.PipelineTemplateService;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PipelineTemplateController.class)
@Import(WebMvcSecurityTestConfig.class)
class PipelineTemplateControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PipelineTemplateService templateService;

    @Test
    void create_asCompanyAdmin_returns201() throws Exception {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        PipelineTemplateResponse response = PipelineTemplateFixtures.sampleResponse(TEMPLATE_ID);

        when(templateService.create(eq(TENANT_ID), eq(USER_ID), any(PipelineTemplateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(json(post("/api/v1/pipeline-templates"), request).with(as("COMPANY_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEMPLATE_ID.toString()))
                .andExpect(jsonPath("$.name").value(response.name()));
    }

    @Test
    void create_asHrManager_returns403() throws Exception {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();

        mockMvc.perform(json(post("/api/v1/pipeline-templates"), request).with(as("HR_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();

        mockMvc.perform(json(post("/api/v1/pipeline-templates"), request))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPANY_ADMIN", "HR_MANAGER"})
    void list_asAuthorizedRole_returns200(String role) throws Exception {
        PipelineTemplateSummaryResponse summary = PipelineTemplateFixtures.sampleSummaryResponse(TEMPLATE_ID);
        when(templateService.list(eq(TENANT_ID), any(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/pipeline-templates").with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(TEMPLATE_ID.toString()))
                .andExpect(jsonPath("$.content[0].name").value(summary.name()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERVIEWER", "CANDIDATE"})
    void list_asUnauthorizedRole_returns403(String role) throws Exception {
        mockMvc.perform(get("/api/v1/pipeline-templates").with(as(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_asCompanyAdmin_returns200() throws Exception {
        PipelineTemplateResponse response = PipelineTemplateFixtures.sampleResponse(TEMPLATE_ID);
        when(templateService.get(TENANT_ID, TEMPLATE_ID, true)).thenReturn(response);

        mockMvc.perform(get("/api/v1/pipeline-templates/{id}", TEMPLATE_ID).with(as("COMPANY_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEMPLATE_ID.toString()));
    }

    @Test
    void update_asCompanyAdmin_returns200() throws Exception {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        PipelineTemplateResponse response = PipelineTemplateFixtures.sampleResponse(TEMPLATE_ID);

        when(templateService.update(eq(TENANT_ID), eq(USER_ID), eq(TEMPLATE_ID), any(PipelineTemplateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(json(put("/api/v1/pipeline-templates/{id}", TEMPLATE_ID), request)
                        .with(as("COMPANY_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEMPLATE_ID.toString()));
    }

    @Test
    void update_asHrManager_returns403() throws Exception {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();

        mockMvc.perform(json(put("/api/v1/pipeline-templates/{id}", TEMPLATE_ID), request)
                        .with(as("HR_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archive_asCompanyAdmin_returns200() throws Exception {
        PipelineTemplateResponse response = PipelineTemplateFixtures.sampleResponse(TEMPLATE_ID);
        when(templateService.archive(TENANT_ID, TEMPLATE_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/pipeline-templates/{id}/archive", TEMPLATE_ID)
                        .with(as("COMPANY_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void duplicate_asCompanyAdmin_returns201() throws Exception {
        PipelineTemplateResponse response = PipelineTemplateFixtures.sampleResponse(TEMPLATE_ID);
        when(templateService.duplicate(TENANT_ID, USER_ID, TEMPLATE_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/pipeline-templates/{id}/duplicate", TEMPLATE_ID)
                        .with(as("COMPANY_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TEMPLATE_ID.toString()));
    }

    @Test
    void delete_asCompanyAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/pipeline-templates/{id}", TEMPLATE_ID)
                        .with(as("COMPANY_ADMIN")))
                .andExpect(status().isNoContent());

        verify(templateService).delete(TENANT_ID, TEMPLATE_ID);
    }

    @Test
    void delete_asHrManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/pipeline-templates/{id}", TEMPLATE_ID)
                        .with(as("HR_MANAGER")))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor as(String role) {
        return asUser(USER_ID, TENANT_ID, role);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }
}
