package com.talentpipe.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.PublicCompanyProfileResponse;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.entity.Tenant;
import com.talentpipe.tenant.mapper.TenantMapper;
import com.talentpipe.tenant.repository.TenantRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TenantService}.
 * Pure Mockito — no Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private ProfileNormalizer normalizer;

    @InjectMocks
    private TenantService tenantService;

    private UUID tenantId;
    private Tenant tenant;
    private TenantResponse tenantResponse;
    private CompanyProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant("Acme Corp", "acme");
        tenantResponse = new TenantResponse(tenantId, "Acme Corp", "acme", null,
                "STANDARD", "ACTIVE", Instant.now());
        profileResponse = buildProfileResponse(tenantId, null, null);
    }

    // ---------------------------------------------------------------- createTenant

    @Test
    void createTenant_withUniqueSubdomain_savesAndReturnsResponse() {
        when(tenantRepository.existsBySubdomain("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        TenantResponse result = tenantService.createTenant("Acme Corp", "acme");

        assertThat(result).isEqualTo(tenantResponse);
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void createTenant_withDuplicateSubdomain_throwsDuplicateResourceException() {
        when(tenantRepository.existsBySubdomain("acme")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant("Acme Corp", "acme"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("acme");

        verify(tenantRepository, never()).save(any());
    }

    @Test
    void createTenant_withNullSubdomain_generatesSubdomainFromName() {
        // The auto-generated subdomain "acme-corp" is free
        when(tenantRepository.existsBySubdomain("acme-corp")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        TenantResponse result = tenantService.createTenant("Acme Corp", null);

        assertThat(result).isNotNull();
        verify(tenantRepository, times(2)).existsBySubdomain("acme-corp");
    }

    @Test
    void createTenant_withBlankSubdomain_generatesSubdomainFromName() {
        when(tenantRepository.existsBySubdomain("acme-corp")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        tenantService.createTenant("Acme Corp", "   ");

        verify(tenantRepository, times(2)).existsBySubdomain("acme-corp");
    }

    @Test
    void createTenant_normalizesSubdomainToLowercase() {
        when(tenantRepository.existsBySubdomain("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        tenantService.createTenant("Acme Corp", "ACME");

        verify(tenantRepository).existsBySubdomain("acme");
    }

    // ---------------------------------------------------------------- generateUniqueSubdomain

    @Test
    void generateUniqueSubdomain_simpleNameProducesKebabCase() {
        when(tenantRepository.existsBySubdomain("acme-corp")).thenReturn(false);

        String result = tenantService.generateUniqueSubdomain("Acme Corp");

        assertThat(result).isEqualTo("acme-corp");
    }

    @Test
    void generateUniqueSubdomain_whenBaseIsTaken_appendsSuffix() {
        when(tenantRepository.existsBySubdomain("acme")).thenReturn(true);
        when(tenantRepository.existsBySubdomain("acme-1")).thenReturn(false);

        String result = tenantService.generateUniqueSubdomain("Acme");

        assertThat(result).isEqualTo("acme-1");
    }

    @Test
    void generateUniqueSubdomain_withSpecialCharacters_removesNonAlphanumeric() {
        when(tenantRepository.existsBySubdomain("acme-inc")).thenReturn(false);

        String result = tenantService.generateUniqueSubdomain("Acme & Inc!");

        assertThat(result).isEqualTo("acme-inc");
    }

    @Test
    void generateUniqueSubdomain_withAllSpecialCharacters_fallsBackToCompany() {
        when(tenantRepository.existsBySubdomain("company")).thenReturn(false);

        String result = tenantService.generateUniqueSubdomain("!!!###");

        assertThat(result).isEqualTo("company");
    }

    @Test
    void generateUniqueSubdomain_withVeryLongName_truncatesTo90Characters() {
        String longName = "A".repeat(200);
        String expected = "a".repeat(90);
        when(tenantRepository.existsBySubdomain(expected)).thenReturn(false);

        String result = tenantService.generateUniqueSubdomain(longName);

        assertThat(result).hasSize(90);
    }

    // ---------------------------------------------------------------- findBySubdomain

    @Test
    void findBySubdomain_existingSubdomain_returnsMappedResponse() {
        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        Optional<TenantResponse> result = tenantService.findBySubdomain("acme");

        assertThat(result).contains(tenantResponse);
    }

    @Test
    void findBySubdomain_normalizesInputToLowercase() {
        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.empty());

        Optional<TenantResponse> result = tenantService.findBySubdomain("ACME");

        assertThat(result).isEmpty();
        verify(tenantRepository).findBySubdomain("acme");
    }

    @Test
    void findBySubdomain_unknownSubdomain_returnsEmpty() {
        when(tenantRepository.findBySubdomain("unknown")).thenReturn(Optional.empty());

        Optional<TenantResponse> result = tenantService.findBySubdomain("unknown");

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------- findById

    @Test
    void findById_existingId_returnsMappedResponse() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(tenantResponse);

        Optional<TenantResponse> result = tenantService.findById(tenantId);

        assertThat(result).contains(tenantResponse);
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        Optional<TenantResponse> result = tenantService.findById(tenantId);

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------- getProfile

    @Test
    void getProfile_existingTenant_returnsMappedProfileResponse() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toProfileResponse(tenant)).thenReturn(profileResponse);

        CompanyProfileResponse result = tenantService.getProfile(tenantId);

        assertThat(result).isEqualTo(profileResponse);
    }

    @Test
    void getProfile_nonExistingTenant_throwsResourceNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getProfile(tenantId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    // ---------------------------------------------------------------- updateProfile

    @Test
    void updateProfile_validRequest_appliesNormalizationAndSaves() {
        UpdateCompanyProfileRequest request = buildUpdateRequest("Acme Corp", "admin@acme.io");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(normalizer.normalizeLine(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(normalizer.normalizeLine(null)).thenReturn(null);
        when(normalizer.normalizeMultiline(null)).thenReturn(null);
        when(normalizer.normalizeUrl(null)).thenReturn(null);
        when(normalizer.normalizeList(null)).thenReturn(Collections.emptyList());
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toProfileResponse(tenant)).thenReturn(profileResponse);

        CompanyProfileResponse result = tenantService.updateProfile(tenantId, request);

        assertThat(result).isEqualTo(profileResponse);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateProfile_nonExistingTenant_throwsResourceNotFoundException() {
        UpdateCompanyProfileRequest request = buildUpdateRequest("Acme", "admin@acme.io");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateProfile(tenantId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_setsListFieldsFromNormalizer() {
        List<String> normalized = List.of("Engineering", "Product");
        UpdateCompanyProfileRequest request = buildUpdateRequest("Acme", "admin@acme.io");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(normalizer.normalizeLine(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(normalizer.normalizeLine(null)).thenReturn(null);
        when(normalizer.normalizeMultiline(null)).thenReturn(null);
        when(normalizer.normalizeUrl(null)).thenReturn(null);
        when(normalizer.normalizeList(any())).thenReturn(normalized);
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toProfileResponse(tenant)).thenReturn(profileResponse);

        tenantService.updateProfile(tenantId, request);

        assertThat(tenant.getDepartments()).isEqualTo(normalized);
    }

    // ---------------------------------------------------------------- updateLogoUrl

    @Test
    void updateLogoUrl_setsLogoUrlAndSaves() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toProfileResponse(tenant)).thenReturn(profileResponse);

        CompanyProfileResponse result = tenantService.updateLogoUrl(tenantId, "https://cdn.example.com/logo.png");

        assertThat(result).isEqualTo(profileResponse);
        assertThat(tenant.getLogoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateLogoUrl_nonExistingTenant_throwsResourceNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateLogoUrl(tenantId, "https://cdn.example.com/logo.png"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- updateCoverImageUrl

    @Test
    void updateCoverImageUrl_setsCoverUrlAndSaves() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toProfileResponse(tenant)).thenReturn(profileResponse);

        CompanyProfileResponse result = tenantService.updateCoverImageUrl(tenantId, "https://cdn.example.com/cover.jpg");

        assertThat(result).isEqualTo(profileResponse);
        assertThat(tenant.getCoverImageUrl()).isEqualTo("https://cdn.example.com/cover.jpg");
    }

    @Test
    void updateCoverImageUrl_nonExistingTenant_throwsResourceNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateCoverImageUrl(tenantId, "https://cdn.example.com/cover.jpg"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- clearLogoUrl

    @Test
    void clearLogoUrl_setsLogoUrlToNull() {
        tenant.setLogoUrl("https://cdn.example.com/logo.png");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        tenantService.clearLogoUrl(tenantId);

        assertThat(tenant.getLogoUrl()).isNull();
        verify(tenantRepository).save(tenant);
    }

    @Test
    void clearLogoUrl_nonExistingTenant_throwsResourceNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.clearLogoUrl(tenantId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- clearCoverImageUrl

    @Test
    void clearCoverImageUrl_setsCoverToNull() {
        tenant.setCoverImageUrl("https://cdn.example.com/cover.jpg");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        tenantService.clearCoverImageUrl(tenantId);

        assertThat(tenant.getCoverImageUrl()).isNull();
        verify(tenantRepository).save(tenant);
    }

    @Test
    void clearCoverImageUrl_nonExistingTenant_throwsResourceNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.clearCoverImageUrl(tenantId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- getPublicProfile

    @Test
    void getPublicProfile_knownSubdomain_returnsPublicResponse() {
        PublicCompanyProfileResponse publicResponse = new PublicCompanyProfileResponse(
                "Acme", "acme", null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.of(tenant));
        when(tenantMapper.toPublicResponse(tenant)).thenReturn(publicResponse);

        Optional<PublicCompanyProfileResponse> result = tenantService.getPublicProfile("acme");

        assertThat(result).contains(publicResponse);
    }

    @Test
    void getPublicProfile_unknownSubdomain_returnsEmpty() {
        when(tenantRepository.findBySubdomain("unknown")).thenReturn(Optional.empty());

        Optional<PublicCompanyProfileResponse> result = tenantService.getPublicProfile("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void getPublicProfile_normalizesSubdomainToLowercase() {
        when(tenantRepository.findBySubdomain("acme")).thenReturn(Optional.empty());

        tenantService.getPublicProfile("ACME");

        verify(tenantRepository).findBySubdomain("acme");
    }

    // ---------------------------------------------------------------- helpers

    private UpdateCompanyProfileRequest buildUpdateRequest(String name, String email) {
        return new UpdateCompanyProfileRequest(
                name, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                email, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private CompanyProfileResponse buildProfileResponse(UUID id, String logoUrl, String coverUrl) {
        return new CompanyProfileResponse(
                id, "Acme Corp", "acme", "STANDARD", "ACTIVE",
                logoUrl, coverUrl, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Instant.now());
    }
}
