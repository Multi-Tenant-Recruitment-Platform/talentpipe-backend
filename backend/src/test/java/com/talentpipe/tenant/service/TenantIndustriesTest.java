package com.talentpipe.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.dto.UpdateTenantSettingsRequest;
import com.talentpipe.tenant.entity.Tenant;
import com.talentpipe.tenant.entity.TenantStatus;
import com.talentpipe.tenant.mapper.TenantMapper;
import com.talentpipe.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the company settings other modules build on: its industries
 * (the main one and up to three more, saved in their canonical spelling, read
 * back in order) and its feature switches. The normalizer and mapper are the
 * real ones; only persistence is mocked.
 */
@ExtendWith(MockitoExtension.class)
class TenantIndustriesTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private TenantRepository tenantRepository;

    private TenantService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new TenantService(tenantRepository, new TenantMapper(), new ProfileNormalizer(new HtmlSanitizer()));
        tenant = new Tenant("Acme", "acme");
    }

    @Test
    void update_storesIndustriesInTheirListedSpelling_withoutTheMainOneOrRepeats() {
        editable();

        service.updateProfile(TENANT, request("information technology",
                List.of("finance and banking", "Information Technology", "FINANCE & BANKING", "healthcare")));

        assertThat(tenant.getIndustry()).isEqualTo("Information Technology");
        assertThat(tenant.getAdditionalIndustries()).containsExactly("Finance & Banking", "Healthcare");
    }

    @Test
    void update_withMoreIndustriesButNoMainOne_isRefused() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        UpdateCompanyProfileRequest request = request(null, List.of("Healthcare"));

        assertThatThrownBy(() -> service.updateProfile(TENANT, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose a main industry before adding more.");
    }

    @Test
    void industriesOf_listsTheMainIndustryFirst() {
        tenant.setIndustry("Education");
        tenant.setAdditionalIndustries(List.of("Healthcare", "Other"));
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));

        assertThat(service.industriesOf(TENANT)).containsExactly("Education", "Healthcare", "Other");
    }

    @Test
    void industriesOf_isEmpty_whenNoMainIndustryIsSet_orTheCompanyIsUnknown() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        UUID unknown = UUID.randomUUID();
        when(tenantRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.industriesOf(TENANT)).isEmpty();
        assertThat(service.industriesOf(unknown)).isEmpty();
    }

    // ---------------------------------------------------------- switches

    @Test
    void updateSettings_changesOnlyTheSwitchesGiven() {
        tenant.setAiScreeningDefault(true);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(tenantRepository.saveAndFlush(tenant)).thenReturn(tenant);

        var settings = service.updateSettings(TENANT, new UpdateTenantSettingsRequest(true, null, true));

        assertThat(settings.blindScreeningEnabled()).isTrue();
        assertThat(settings.aiScreeningDefault()).isTrue();
        assertThat(settings.candidateMatchingDefault()).isTrue();
    }

    @Test
    void updateSettings_ofASuspendedCompany_isRefused() {
        // Tenant has no status setter (suspension arrives with platform admin).
        ReflectionTestUtils.setField(tenant, "status", TenantStatus.SUSPENDED);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        UpdateTenantSettingsRequest request = new UpdateTenantSettingsRequest(true, null, null);

        assertThatThrownBy(() -> service.updateSettings(TENANT, request))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getSettings_startsWithEverySwitchOff() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));

        assertThat(service.getSettings(TENANT)).satisfies(settings -> {
            assertThat(settings.blindScreeningEnabled()).isFalse();
            assertThat(settings.aiScreeningDefault()).isFalse();
            assertThat(settings.candidateMatchingDefault()).isFalse();
        });
    }

    private void editable() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static UpdateCompanyProfileRequest request(String industry, List<String> additionalIndustries) {
        return new UpdateCompanyProfileRequest(
                "Acme", null, industry, null, null, null,
                null, null, null, null, null, null, null, null,
                "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null,
                null, null, null, additionalIndustries);
    }
}
