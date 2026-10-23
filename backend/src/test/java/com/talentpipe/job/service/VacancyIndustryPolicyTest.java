package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.tenant.service.TenantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for which industry a vacancy is filed under. */
@ExtendWith(MockitoExtension.class)
class VacancyIndustryPolicyTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private TenantService tenantService;

    private VacancyIndustryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new VacancyIndustryPolicy(tenantService);
    }

    @Test
    void aNewVacancyWithNoIndustry_takesTheCompanysMainOne() {
        companyWorksIn("Information Technology", "Finance & Banking");

        assertThat(policy.resolve(TENANT, " ", null)).isEqualTo("Information Technology");
    }

    @Test
    void aCompanyWithNoIndustry_filesItsVacanciesUnderNone() {
        companyWorksIn();

        assertThat(policy.resolve(TENANT, null, null)).isNull();
    }

    @Test
    void anyOfTheCompanysIndustries_isAccepted_inAnySpelling() {
        companyWorksIn("Information Technology", "Finance & Banking");

        assertThat(policy.resolve(TENANT, "finance and banking", null)).isEqualTo("Finance & Banking");
    }

    @Test
    void anIndustryTheCompanyIsNotIn_isRefused_namingTheOnesItIs() {
        companyWorksIn("Information Technology", "Finance & Banking");

        assertThatThrownBy(() -> policy.resolve(TENANT, "Healthcare", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Choose one of your company's industries: Information Technology, Finance & Banking.");
    }

    @Test
    void anyIndustry_isRefused_whileTheCompanyHasNone() {
        companyWorksIn();

        assertThatThrownBy(() -> policy.resolve(TENANT, "Healthcare", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Add your company's industry to its profile");
    }

    @Test
    void aValueThatIsNoIndustryAtAll_isRefused() {
        companyWorksIn("Other");

        assertThatThrownBy(() -> policy.resolve(TENANT, "Space Tourism", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anEdit_keepsTheCurrentIndustry_evenIfTheCompanyHasDroppedIt() {
        assertThat(policy.resolve(TENANT, null, "Healthcare")).isEqualTo("Healthcare");
        assertThat(policy.resolve(TENANT, "healthcare", "Healthcare")).isEqualTo("Healthcare");
        verifyNoInteractions(tenantService);
    }

    @Test
    void aDuplicate_keepsTheSourcesIndustry_onlyWhileTheCompanyIsStillInIt() {
        companyWorksIn("Education", "Healthcare");

        assertThat(policy.forDuplicate(TENANT, "Healthcare")).isEqualTo("Healthcare");
        assertThat(policy.forDuplicate(TENANT, "Manufacturing")).isEqualTo("Education");
        assertThat(policy.forDuplicate(TENANT, null)).isEqualTo("Education");
    }

    @Test
    void aDuplicate_ofACompanyWithNoIndustry_hasNone() {
        companyWorksIn();

        assertThat(policy.forDuplicate(TENANT, "Healthcare")).isNull();
    }

    private void companyWorksIn(String... industries) {
        when(tenantService.industriesOf(TENANT)).thenReturn(List.of(industries));
    }
}
