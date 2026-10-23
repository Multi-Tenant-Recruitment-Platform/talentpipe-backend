package com.talentpipe.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.entity.Tenant;
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

/**
 * Unit tests for a company's industries: the main one and up to three more,
 * saved in their canonical spelling, and read back by other modules in order.
 * The normalizer is the real one; only persistence is mocked.
 */
@ExtendWith(MockitoExtension.class)
class TenantIndustriesTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantMapper tenantMapper;

    private TenantService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new TenantService(tenantRepository, tenantMapper, new ProfileNormalizer(new HtmlSanitizer()));
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
