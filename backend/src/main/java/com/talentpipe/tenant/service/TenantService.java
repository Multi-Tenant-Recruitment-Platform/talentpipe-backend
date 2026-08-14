package com.talentpipe.tenant.service;

import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.PublicCompanyProfileResponse;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.entity.Tenant;
import com.talentpipe.tenant.mapper.TenantMapper;
import com.talentpipe.tenant.repository.TenantRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant module's public API. Other modules (auth, and later job,
 * pipeline, …) interact with tenants exclusively through this service and its
 * DTOs — the {@code Tenant} entity never crosses the module boundary.
 *
 * <p>Security note: every mutating method re-verifies that the supplied
 * {@code tenantId} matches the record being modified. This is a defence-in-depth
 * check — the controller already derives the tenant from the JWT, but a future
 * internal caller reaching this service another way must not be able to
 * bypass the scope check (per the double-check convention in DECISIONS.md).</p>
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final ProfileNormalizer normalizer;

    public TenantService(TenantRepository tenantRepository,
                         TenantMapper tenantMapper,
                         ProfileNormalizer normalizer) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
        this.normalizer = normalizer;
    }

    /**
     * Creates a tenant during company onboarding. Participates in the
     * caller's transaction so tenant + first admin are created atomically.
     *
     * @throws DuplicateResourceException when the subdomain is already taken (409)
     */
    @Transactional
    public TenantResponse createTenant(String name, String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            subdomain = generateUniqueSubdomain(name);
        }
        String normalizedSubdomain = normalize(subdomain);
        if (tenantRepository.existsBySubdomain(normalizedSubdomain)) {
            throw new DuplicateResourceException(
                    "Subdomain '" + normalizedSubdomain + "' is already taken");
        }
        Tenant tenant = tenantRepository.save(new Tenant(name.trim(), normalizedSubdomain));
        return tenantMapper.toResponse(tenant);
    }

    /**
     * Dynamically generates a clean, unique subdomain based on the company name.
     */
    public String generateUniqueSubdomain(String companyName) {
        String base = companyName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-");

        if (base.startsWith("-")) base = base.substring(1);
        if (base.endsWith("-")) base = base.substring(0, base.length() - 1);

        if (base.isEmpty()) {
            base = "company";
        }
        if (base.length() > 90) {
            base = base.substring(0, 90);
        }

        String candidate = base;
        int suffix = 1;
        while (tenantRepository.existsBySubdomain(candidate)) {
            String suffixStr = "-" + suffix;
            if (base.length() + suffixStr.length() > 100) {
                base = base.substring(0, 100 - suffixStr.length());
            }
            candidate = base + suffixStr;
            suffix++;
        }
        return candidate;
    }

    /** Resolves a tenant by subdomain (used for login tenant resolution). */
    @Transactional(readOnly = true)
    public Optional<TenantResponse> findBySubdomain(String subdomain) {
        return tenantRepository.findBySubdomain(normalize(subdomain))
                .map(tenantMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<TenantResponse> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(tenantMapper::toResponse);
    }

    // ---------------------------------------------------------- profile API

    /**
     * Returns the full company profile for the given tenant.
     *
     * <p>Used by {@code GET /api/v1/tenant}. The tenantId comes exclusively
     * from the JWT claim via {@code UserPrincipal.tenantId()}.</p>
     *
     * @throws ResourceNotFoundException if the tenant does not exist (404)
     */
    @Transactional(readOnly = true)
    public CompanyProfileResponse getProfile(UUID tenantId) {
        return tenantMapper.toProfileResponse(requireTenant(tenantId));
    }

    /**
     * Updates editable profile fields for the given tenant.
     *
     * <p>Used by {@code PATCH /api/v1/tenant}. The tenantId comes exclusively
     * from the JWT claim. Immutable fields (subdomain, planTier, status,
     * logoUrl, coverImageUrl) are never touched here; image changes go through
     * {@code TenantMediaService}.</p>
     *
     * <p>Normalization is applied before persistence: whitespace is trimmed,
     * URLs get a scheme, list values are deduplicated. The full normalized
     * profile is returned so the frontend can fully rehydrate state.</p>
     *
     * @throws ResourceNotFoundException if the tenant does not exist (404)
     */
    @Transactional
    public CompanyProfileResponse updateProfile(UUID tenantId, UpdateCompanyProfileRequest request) {
        Tenant tenant = requireTenant(tenantId);

        // ---- single-line fields
        tenant.setName(normalizer.normalizeLine(request.name()));
        tenant.setTagline(normalizer.normalizeLine(request.tagline()));
        tenant.setIndustry(normalizer.normalizeLine(request.industry()));
        tenant.setCompanyType(normalizer.normalizeLine(request.companyType()));
        tenant.setSize(normalizer.normalizeLine(request.size()));
        tenant.setLegalName(normalizer.normalizeLine(request.legalName()));
        tenant.setRegistrationNumber(normalizer.normalizeLine(request.registrationNumber()));
        tenant.setTimezone(normalizer.normalizeLine(request.timezone()));
        tenant.setCurrency(normalizer.normalizeLine(request.currency()));
        tenant.setLanguage(normalizer.normalizeLine(request.language()));
        tenant.setEmail(normalizer.normalizeLine(request.email()));
        tenant.setHrEmail(normalizer.normalizeLine(request.hrEmail()));
        tenant.setPhone(normalizer.normalizeLine(request.phone()));
        tenant.setAlternativePhone(normalizer.normalizeLine(request.alternativePhone()));
        tenant.setAddress(normalizer.normalizeLine(request.address()));
        tenant.setCity(normalizer.normalizeLine(request.city()));
        tenant.setState(normalizer.normalizeLine(request.state()));
        tenant.setPostalCode(normalizer.normalizeLine(request.postalCode()));
        tenant.setCountry(normalizer.normalizeLine(request.country()));

        // ---- multiline fields (preserve newlines)
        tenant.setDescription(normalizer.normalizeMultiline(request.description()));
        tenant.setCulture(normalizer.normalizeMultiline(request.culture()));
        tenant.setMission(normalizer.normalizeMultiline(request.mission()));
        tenant.setVision(normalizer.normalizeMultiline(request.vision()));

        // ---- numeric fields (null = clear)
        tenant.setEmployeeCount(request.employeeCount());
        tenant.setFoundedYear(request.foundedYear());

        // ---- URL fields (scheme normalization)
        tenant.setWebsite(normalizer.normalizeUrl(request.website()));
        tenant.setLinkedinUrl(normalizer.normalizeUrl(request.linkedinUrl()));
        tenant.setFacebookUrl(normalizer.normalizeUrl(request.facebookUrl()));
        tenant.setTwitterUrl(normalizer.normalizeUrl(request.twitterUrl()));
        tenant.setInstagramUrl(normalizer.normalizeUrl(request.instagramUrl()));

        // ---- list fields (trim, deduplicate, blank-filter)
        tenant.setValues(normalizer.normalizeList(request.values()));
        tenant.setBenefits(normalizer.normalizeList(request.benefits()));
        tenant.setWorkModes(normalizer.normalizeList(request.workModes()));
        tenant.setOfficeLocations(normalizer.normalizeList(request.officeLocations()));
        tenant.setDepartments(normalizer.normalizeList(request.departments()));
        tenant.setTeams(normalizer.normalizeList(request.teams()));
        tenant.setBusinessUnits(normalizer.normalizeList(request.businessUnits()));
        tenant.setEmploymentTypes(normalizer.normalizeList(request.employmentTypes()));
        tenant.setJobCategories(normalizer.normalizeList(request.jobCategories()));
        tenant.setJobFamilies(normalizer.normalizeList(request.jobFamilies()));
        tenant.setJobLevels(normalizer.normalizeList(request.jobLevels()));
        tenant.setJobTitles(normalizer.normalizeList(request.jobTitles()));

        Tenant saved = tenantRepository.save(tenant);
        return tenantMapper.toProfileResponse(saved);
    }

    /**
     * Returns the curated public profile for a given subdomain.
     *
     * <p>Used by {@code GET /api/v1/public/companies/{subdomain}}. No
     * authentication required. Returns empty Optional when not found so the
     * controller can return 404 without leaking which subdomains exist.</p>
     */
    @Transactional(readOnly = true)
    public Optional<PublicCompanyProfileResponse> getPublicProfile(String subdomain) {
        return tenantRepository.findBySubdomain(normalize(subdomain))
                .map(tenantMapper::toPublicResponse);
    }

    /**
     * Updates the logo URL on the tenant record. Called by {@code TenantMediaService}
     * after a successful upload. The URL is the final CDN-backed public URL.
     *
     * @throws ResourceNotFoundException if the tenant does not exist (404)
     */
    @Transactional
    public CompanyProfileResponse updateLogoUrl(UUID tenantId, String logoUrl) {
        Tenant tenant = requireTenant(tenantId);
        tenant.setLogoUrl(logoUrl);
        return tenantMapper.toProfileResponse(tenantRepository.save(tenant));
    }

    /**
     * Updates the cover image URL on the tenant record.
     *
     * @throws ResourceNotFoundException if the tenant does not exist (404)
     */
    @Transactional
    public CompanyProfileResponse updateCoverImageUrl(UUID tenantId, String coverImageUrl) {
        Tenant tenant = requireTenant(tenantId);
        tenant.setCoverImageUrl(coverImageUrl);
        return tenantMapper.toProfileResponse(tenantRepository.save(tenant));
    }

    /**
     * Clears the logo URL. Idempotent — calling when already null is safe.
     *
     * @throws ResourceNotFoundException if the tenant does not exist (404)
     */
    @Transactional
    public void clearLogoUrl(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.setLogoUrl(null);
        tenantRepository.save(tenant);
    }

    /**
     * Clears the cover image URL. Idempotent.
     *
     * @throws ResourceNotFoundException if the tenant does not exist (404)
     */
    @Transactional
    public void clearCoverImageUrl(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.setCoverImageUrl(null);
        tenantRepository.save(tenant);
    }

    // ---------------------------------------------------------------- private

    /**
     * Fetches the tenant or throws 404. The same exception is used for a
     * genuinely missing tenant and for a cross-tenant attempt so existence
     * does not leak across tenant boundaries (DECISIONS.md convention).
     */
    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    /** Subdomains are case-insensitive identifiers: store and compare lowercase. */
    private String normalize(String subdomain) {
        return subdomain == null ? null : subdomain.trim().toLowerCase(Locale.ROOT);
    }
}
