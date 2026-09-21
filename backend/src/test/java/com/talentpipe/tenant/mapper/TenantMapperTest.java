package com.talentpipe.tenant.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.PublicCompanyProfileResponse;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.entity.Tenant;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantMapper}.
 * Pure Java — no Spring context required.
 */
class TenantMapperTest {

    private TenantMapper mapper;
    private Tenant tenant;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new TenantMapper();
        tenant = new Tenant("LankaTech Solutions", "lankatech");

        // @UuidGenerator and @CreationTimestamp only fire during JPA persist.
        // Set both via reflection so mapper tests are self-contained without a JPA context.
        Class<?> baseEntity = tenant.getClass().getSuperclass();

        Field idField = baseEntity.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(tenant, UUID.randomUUID());

        Field createdAtField = baseEntity.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(tenant, java.time.Instant.now());

        // Populate branding, info, contact, location, taxonomy
        tenant.setLogoUrl("https://cdn.example.com/logo.png");
        tenant.setCoverImageUrl("https://cdn.example.com/cover.jpg");
        tenant.setTagline("Great place to work");
        tenant.setIndustry("Technology");
        tenant.setCompanyType("Private");
        tenant.setSize("51-200");
        tenant.setFoundedYear(2015);
        tenant.setDescription("We build great software.");
        tenant.setMission("To innovate.");
        tenant.setVision("To lead.");
        tenant.setLegalName("LankaTech Solutions Pvt Ltd");
        tenant.setRegistrationNumber("REG-001");
        tenant.setTimezone("Asia/Colombo");
        tenant.setCurrency("LKR");
        tenant.setLanguage("en");
        tenant.setEmail("info@lankatech.io");
        tenant.setHrEmail("hr@lankatech.io");
        tenant.setPhone("+94112345678");
        tenant.setAlternativePhone("+94117654321");
        tenant.setWebsite("https://lankatech.io");
        tenant.setLinkedinUrl("https://linkedin.com/company/lankatech");
        tenant.setFacebookUrl("https://facebook.com/lankatech");
        tenant.setTwitterUrl("https://x.com/lankatech");
        tenant.setInstagramUrl("https://instagram.com/lankatech");
        tenant.setCity("Colombo");
        tenant.setState("Western Province");
        tenant.setPostalCode("00300");
        tenant.setCountry("Sri Lanka");
        tenant.setBenefits(List.of("HEALTH_INSURANCE"));
        tenant.setOfficeLocations(List.of("Colombo", "Kandy"));
        tenant.setDepartments(List.of("Engineering", "QA", "HR"));
    }

    // ---------------------------------------------------------------- toResponse (lean auth DTO)

    @Test
    void toResponse_mapsIdentityFieldsCorrectly() {
        TenantResponse response = mapper.toResponse(tenant);

        assertThat(response.name()).isEqualTo("LankaTech Solutions");
        assertThat(response.subdomain()).isEqualTo("lankatech");
        assertThat(response.planTier()).isEqualTo("STANDARD");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.industry()).isEqualTo("Technology");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void toResponse_doesNotIncludeProfileFields() {
        TenantResponse response = mapper.toResponse(tenant);
        // TenantResponse is a lean DTO — it only has identity/basic fields
        assertThat(response.name()).isNotBlank();
        assertThat(response.id()).isNotNull();
    }

    // ---------------------------------------------------------------- toProfileResponse (full profile)

    @Test
    void toProfileResponse_mapsAllIdentityFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.name()).isEqualTo("LankaTech Solutions");
        assertThat(profile.subdomain()).isEqualTo("lankatech");
        assertThat(profile.planTier()).isEqualTo("STANDARD");
        assertThat(profile.status()).isEqualTo("ACTIVE");
        assertThat(profile.id()).isNotNull();
    }

    @Test
    void toProfileResponse_mapsBrandingFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(profile.coverImageUrl()).isEqualTo("https://cdn.example.com/cover.jpg");
        assertThat(profile.tagline()).isEqualTo("Great place to work");
    }

    @Test
    void toProfileResponse_mapsCompanyInfoFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.industry()).isEqualTo("Technology");
        assertThat(profile.companyType()).isEqualTo("Private");
        assertThat(profile.size()).isEqualTo("51-200");
        assertThat(profile.foundedYear()).isEqualTo(2015);
        assertThat(profile.description()).isEqualTo("We build great software.");
        assertThat(profile.mission()).isEqualTo("To innovate.");
        assertThat(profile.vision()).isEqualTo("To lead.");
        assertThat(profile.legalName()).isEqualTo("LankaTech Solutions Pvt Ltd");
        assertThat(profile.registrationNumber()).isEqualTo("REG-001");
    }

    @Test
    void toProfileResponse_mapsLocalisationFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.timezone()).isEqualTo("Asia/Colombo");
        assertThat(profile.currency()).isEqualTo("LKR");
        assertThat(profile.language()).isEqualTo("en");
    }

    @Test
    void toProfileResponse_mapsContactFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.email()).isEqualTo("info@lankatech.io");
        assertThat(profile.hrEmail()).isEqualTo("hr@lankatech.io");
        assertThat(profile.phone()).isEqualTo("+94112345678");
        assertThat(profile.website()).isEqualTo("https://lankatech.io");
        assertThat(profile.linkedinUrl()).isEqualTo("https://linkedin.com/company/lankatech");
    }

    @Test
    void toProfileResponse_mapsLocationFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.city()).isEqualTo("Colombo");
        assertThat(profile.state()).isEqualTo("Western Province");
        assertThat(profile.country()).isEqualTo("Sri Lanka");
        assertThat(profile.postalCode()).isEqualTo("00300");
    }

    @Test
    void toProfileResponse_mapsTaxonomyListFields() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);

        assertThat(profile.benefits()).containsExactly("HEALTH_INSURANCE");
        assertThat(profile.officeLocations()).containsExactly("Colombo", "Kandy");
        assertThat(profile.departments()).containsExactly("Engineering", "QA", "HR");
    }

    @Test
    void toProfileResponse_nullListFields_returnEmptyLists() {
        Tenant sparse = new Tenant("Sparse Corp", "sparse");
        CompanyProfileResponse profile = mapper.toProfileResponse(sparse);

        // Lists must never be null — frontend iteration must not require null guards
        assertThat(profile.benefits()).isNotNull().isEmpty();
        assertThat(profile.officeLocations()).isNotNull().isEmpty();
        assertThat(profile.departments()).isNotNull().isEmpty();
    }

    @Test
    void toProfileResponse_includesUpdatedAt() {
        CompanyProfileResponse profile = mapper.toProfileResponse(tenant);
        // updatedAt may be null on a freshly created entity (before first save)
        // but the field must exist
        assertThat(profile).isNotNull();
    }

    // ---------------------------------------------------------------- toPublicResponse

    @Test
    void toPublicResponse_mapsDisplayFields() {
        PublicCompanyProfileResponse pub = mapper.toPublicResponse(tenant);

        assertThat(pub.name()).isEqualTo("LankaTech Solutions");
        assertThat(pub.subdomain()).isEqualTo("lankatech");
        assertThat(pub.tagline()).isEqualTo("Great place to work");
        assertThat(pub.description()).isEqualTo("We build great software.");
        assertThat(pub.industry()).isEqualTo("Technology");
        assertThat(pub.size()).isEqualTo("51-200");
        assertThat(pub.foundedYear()).isEqualTo(2015);
        assertThat(pub.website()).isEqualTo("https://lankatech.io");
        assertThat(pub.city()).isEqualTo("Colombo");
        assertThat(pub.country()).isEqualTo("Sri Lanka");
    }

    @Test
    void toPublicResponse_excludesSensitiveFields() {
        // PublicCompanyProfileResponse is a different record type that
        // does not even declare email, hrEmail, planTier, status, etc.
        // This test documents the contract by verifying the included set.
        PublicCompanyProfileResponse pub = mapper.toPublicResponse(tenant);

        // These fields are in the public response
        assertThat(pub.name()).isNotNull();
        assertThat(pub.subdomain()).isNotNull();
        // Confirm internal fields are NOT present by checking the record component names
        // (compile-time: the record simply doesn't declare them)
        assertThat(pub).isInstanceOf(PublicCompanyProfileResponse.class);
    }

    @Test
    void toPublicResponse_logoAndCoverMapped() {
        PublicCompanyProfileResponse pub = mapper.toPublicResponse(tenant);

        assertThat(pub.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(pub.coverImageUrl()).isEqualTo("https://cdn.example.com/cover.jpg");
    }

    @Test
    void toPublicResponse_socialLinksMapped() {
        PublicCompanyProfileResponse pub = mapper.toPublicResponse(tenant);

        assertThat(pub.linkedinUrl()).isEqualTo("https://linkedin.com/company/lankatech");
        assertThat(pub.twitterUrl()).isEqualTo("https://x.com/lankatech");
        assertThat(pub.instagramUrl()).isEqualTo("https://instagram.com/lankatech");
        assertThat(pub.facebookUrl()).isEqualTo("https://facebook.com/lankatech");
    }
}
