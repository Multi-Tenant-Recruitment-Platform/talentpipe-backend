package com.talentpipe.pipeline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.pipeline.PipelineTemplateFixtures;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.entity.PipelineTemplate;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.mapper.PipelineTemplateMapper;
import com.talentpipe.pipeline.repository.PipelineTemplateRepository;
import com.talentpipe.pipeline.validator.PipelineTemplateValidator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PipelineTemplateServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private PipelineTemplateRepository templateRepository;

    @Mock
    private PipelineTemplateValidator validator;

    private PipelineTemplateMapper mapper;
    private PipelineTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = new PipelineTemplateMapper();
        service = new PipelineTemplateServiceImpl(templateRepository, validator, mapper);
    }

    @Test
    void create_validRequest_savesAndReturnsResponse() {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        when(validator.sanitizeText(request.name())).thenReturn(request.name());
        when(validator.sanitizeText(request.description())).thenReturn(request.description());
        when(templateRepository.save(any(PipelineTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineTemplateResponse response = service.create(TENANT_ID, USER_ID, request);

        verify(validator).validate(TENANT_ID, null, request);
        verify(templateRepository).save(any(PipelineTemplate.class));
        assertNotNull(response);
        assertEquals(request.name(), response.name());
        assertEquals(4, response.stageCount());
    }

    @Test
    void get_existingActiveTemplate_asAdmin_returnsResponse() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        PipelineTemplateResponse response = service.get(TENANT_ID, TEMPLATE_ID, true);

        assertNotNull(response);
        assertEquals(entity.getName(), response.name());
    }

    @Test
    void get_existingArchivedTemplate_asAdmin_returnsResponse() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        entity.archive();
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        PipelineTemplateResponse response = service.get(TENANT_ID, TEMPLATE_ID, true);

        assertNotNull(response);
        assertSame(PipelineTemplateStatus.ARCHIVED, response.status());
    }

    @Test
    void get_existingActiveTemplate_asNonAdmin_returnsResponse() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        PipelineTemplateResponse response = service.get(TENANT_ID, TEMPLATE_ID, false);

        assertNotNull(response);
        assertEquals(entity.getName(), response.name());
    }

    @Test
    void get_archivedTemplate_asNonAdmin_throwsResourceNotFoundException() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        entity.archive();
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        assertThrows(ResourceNotFoundException.class, () -> service.get(TENANT_ID, TEMPLATE_ID, false));
    }

    @Test
    void get_missingTemplate_throwsResourceNotFoundException() {
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(TENANT_ID, TEMPLATE_ID, true));
    }

    @Test
    void list_asAdmin_withArchived_returnsAll() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        Pageable pageable = PageRequest.of(0, 20);
        when(templateRepository.findByTenantId(TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<PipelineTemplateSummaryResponse> page = service.list(TENANT_ID, "", true, true, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(entity.getName(), page.getContent().get(0).name());
    }

    @Test
    void list_asNonAdmin_withIncludeArchivedTrue_forcesActiveOnly() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        Pageable pageable = PageRequest.of(0, 20);
        when(templateRepository.findByTenantIdAndStatus(TENANT_ID, PipelineTemplateStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<PipelineTemplateSummaryResponse> page = service.list(TENANT_ID, "", true, false, pageable);

        assertEquals(1, page.getTotalElements());
        verify(templateRepository, never()).findByTenantId(TENANT_ID, pageable);
    }

    @Test
    void list_withSearchQuery_filtersCorrectly() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        Pageable pageable = PageRequest.of(0, 20);
        when(templateRepository.findByTenantIdAndStatusAndNameContainingIgnoreCase(
                TENANT_ID, PipelineTemplateStatus.ACTIVE, "engineer", pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<PipelineTemplateSummaryResponse> page = service.list(TENANT_ID, "engineer", false, true, pageable);

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void list_withSearchAndArchived_filtersCorrectly() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        Pageable pageable = PageRequest.of(0, 20);
        when(templateRepository.findByTenantIdAndNameContainingIgnoreCase(TENANT_ID, "engineer", pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<PipelineTemplateSummaryResponse> page = service.list(TENANT_ID, "engineer", true, true, pageable);

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void update_activeTemplate_succeeds() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();

        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(validator.sanitizeText(request.name())).thenReturn(request.name());
        when(validator.sanitizeText(request.description())).thenReturn(request.description());
        when(templateRepository.save(entity)).thenReturn(entity);

        PipelineTemplateResponse response = service.update(TENANT_ID, USER_ID, TEMPLATE_ID, request);

        verify(validator).validate(TENANT_ID, TEMPLATE_ID, request);
        assertNotNull(response);
    }

    @Test
    void update_archivedTemplate_throwsBusinessRuleException() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        entity.archive();
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();

        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        assertThrows(BusinessRuleException.class, () -> service.update(TENANT_ID, USER_ID, TEMPLATE_ID, request));
    }

    @Test
    void archive_activeTemplate_setsArchived() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(templateRepository.save(entity)).thenReturn(entity);

        PipelineTemplateResponse response = service.archive(TENANT_ID, TEMPLATE_ID);

        assertSame(PipelineTemplateStatus.ARCHIVED, response.status());
        assertTrue(entity.isArchived());
    }

    @Test
    void archive_alreadyArchived_isIdempotent() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        entity.archive();
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        PipelineTemplateResponse response = service.archive(TENANT_ID, TEMPLATE_ID);

        assertSame(PipelineTemplateStatus.ARCHIVED, response.status());
        verify(templateRepository, never()).save(entity);
    }

    @Test
    void duplicate_createsCopyWithAppendedName() {
        PipelineTemplate source = PipelineTemplateFixtures.createEntity(TENANT_ID);
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(source));
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                TENANT_ID, source.getName() + " (Copy)", PipelineTemplateStatus.ACTIVE))
                .thenReturn(false);
        when(templateRepository.save(any(PipelineTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineTemplateResponse response = service.duplicate(TENANT_ID, USER_ID, TEMPLATE_ID);

        assertNotNull(response);
        assertEquals(source.getName() + " (Copy)", response.name());
        assertEquals(source.getStages().size(), response.stageCount());
    }

    @Test
    void duplicate_whenCopyExists_incrementsIndex() {
        PipelineTemplate source = PipelineTemplateFixtures.createEntity(TENANT_ID);
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(source));
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                TENANT_ID, source.getName() + " (Copy)", PipelineTemplateStatus.ACTIVE))
                .thenReturn(true);
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                TENANT_ID, source.getName() + " (Copy 2)", PipelineTemplateStatus.ACTIVE))
                .thenReturn(false);
        when(templateRepository.save(any(PipelineTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineTemplateResponse response = service.duplicate(TENANT_ID, USER_ID, TEMPLATE_ID);

        assertEquals(source.getName() + " (Copy 2)", response.name());
    }

    @Test
    void delete_existingTemplate_deletesFromRepo() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        service.delete(TENANT_ID, TEMPLATE_ID);

        verify(templateRepository).delete(entity);
    }

    @Test
    void delete_missingTemplate_throwsResourceNotFoundException() {
        when(templateRepository.findWithStagesByIdAndTenantId(TEMPLATE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(TENANT_ID, TEMPLATE_ID));
    }
}
