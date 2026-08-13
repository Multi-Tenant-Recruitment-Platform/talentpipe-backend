package com.talentpipe.tenant.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.StoredImage;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.entity.Tenant;
import com.talentpipe.tenant.mapper.TenantMapper;
import com.talentpipe.tenant.repository.TenantRepository;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The tenant module's public API. Other modules (auth, and later job,
 * pipeline, …) interact with tenants exclusively through this service and its
 * DTOs — the {@code Tenant} entity never crosses the module boundary.
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
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

    // ------------------------------------------------------- company profile

    /**
     * The caller's own company profile (PB-005).
     *
     * @throws AccessDeniedException     if the caller has no tenant (SUPER_ADMIN)
     * @throws ResourceNotFoundException if the tenant no longer exists (404)
     */
    @Transactional(readOnly = true)
    public CompanyProfileResponse getProfile(UUID tenantId) {
        return tenantMapper.toProfileResponse(require(tenantId));
    }

    /**
     * Replaces the editable fields of the caller's own profile.
     *
     * <p>Full replacement, matching what the settings form submits: it always
     * sends the complete editable set, so a null here means "cleared". The
     * images are untouched — they have their own endpoints, and a text save
     * must never be the thing that erases a logo.</p>
     */
    @Transactional
    public CompanyProfileResponse updateProfile(UUID tenantId, UpdateCompanyProfileRequest request) {
        Tenant tenant = require(tenantId);

        tenant.setName(request.name().trim());
        tenant.setTagline(text(request.tagline()));
        tenant.setIndustry(text(request.industry()));
        tenant.setCompanyType(text(request.companyType()));
        tenant.setSize(text(request.size()));
        tenant.setEmployeeCount(request.employeeCount());
        tenant.setFoundedYear(request.foundedYear());

        tenant.setDescription(text(request.description()));
        tenant.setCulture(text(request.culture()));
        tenant.setMission(text(request.mission()));
        tenant.setVision(text(request.vision()));
        tenant.setValues(list(request.values()));
        tenant.setBenefits(list(request.benefits()));

        tenant.setLegalName(text(request.legalName()));
        tenant.setRegistrationNumber(text(request.registrationNumber()));
        tenant.setWorkModes(list(request.workModes()));
        tenant.setTimezone(text(request.timezone()));
        tenant.setCurrency(text(request.currency()));
        tenant.setLanguage(text(request.language()));

        // Emails are identifiers: store them lowercase so they compare cleanly.
        tenant.setEmail(lower(request.email()));
        tenant.setHrEmail(lower(request.hrEmail()));
        tenant.setPhone(text(request.phone()));
        tenant.setAlternativePhone(text(request.alternativePhone()));
        tenant.setWebsite(text(request.website()));
        tenant.setLinkedinUrl(text(request.linkedinUrl()));
        tenant.setFacebookUrl(text(request.facebookUrl()));
        tenant.setTwitterUrl(text(request.twitterUrl()));
        tenant.setInstagramUrl(text(request.instagramUrl()));

        tenant.setAddress(text(request.address()));
        tenant.setCity(text(request.city()));
        tenant.setState(text(request.state()));
        tenant.setPostalCode(text(request.postalCode()));
        tenant.setCountry(text(request.country()));
        tenant.setOfficeLocations(list(request.officeLocations()));

        tenant.setDepartments(list(request.departments()));
        tenant.setTeams(list(request.teams()));
        tenant.setBusinessUnits(list(request.businessUnits()));
        tenant.setEmploymentTypes(list(request.employmentTypes()));
        tenant.setJobCategories(list(request.jobCategories()));
        tenant.setJobFamilies(list(request.jobFamilies()));
        tenant.setJobLevels(list(request.jobLevels()));
        tenant.setJobTitles(list(request.jobTitles()));

        return tenantMapper.toProfileResponse(persist(tenant));
    }

    /**
     * Content types accepted for a brand image.
     *
     * <p>SVG is deliberately excluded. It is an XML document that can carry
     * script, and these images are served from the platform's own origin — a
     * stored SVG would be a self-hosted XSS vector aimed at every viewer of the
     * company page. Raster formats cannot execute.</p>
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * Stores a brand image against the caller's tenant.
     *
     * @throws BusinessRuleException if the upload is empty, too large, or not
     *         an accepted image type (422)
     */
    @Transactional
    public CompanyProfileResponse storeImage(UUID tenantId, CompanyImageKind kind, MultipartFile file) {
        Tenant tenant = require(tenantId);

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No image was uploaded");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessRuleException(
                    "Unsupported image type. Use PNG, JPEG, WebP or GIF");
        }
        if (file.getSize() > kind.maxBytes()) {
            throw new BusinessRuleException(
                    "That image is too large. The limit is " + (kind.maxBytes() / (1024 * 1024)) + " MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("That image could not be read");
        }

        apply(tenant, kind, bytes, contentType);
        return tenantMapper.toProfileResponse(persist(tenant));
    }

    /** Drops a brand image. Idempotent: removing an absent image is a no-op. */
    @Transactional
    public CompanyProfileResponse removeImage(UUID tenantId, CompanyImageKind kind) {
        Tenant tenant = require(tenantId);
        apply(tenant, kind, null, null);
        return tenantMapper.toProfileResponse(persist(tenant));
    }

    /**
     * Reads a stored image for public serving.
     *
     * <p>Takes the tenant id from the URL rather than a token: brand images are
     * public by nature — they appear on job adverts and the company page — and
     * an {@code <img>} tag cannot send an Authorization header. Only the two
     * image columns are exposed this way; nothing else about the tenant is.</p>
     *
     * @throws ResourceNotFoundException if the tenant or the image is absent (404)
     */
    @Transactional(readOnly = true)
    public StoredImage loadImage(UUID tenantId, CompanyImageKind kind) {
        Tenant tenant = require(tenantId);
        byte[] bytes = kind == CompanyImageKind.LOGO ? tenant.getLogoImage() : tenant.getCoverImage();
        String contentType = kind == CompanyImageKind.LOGO
                ? tenant.getLogoContentType()
                : tenant.getCoverContentType();
        if (bytes == null || contentType == null) {
            throw new ResourceNotFoundException("No " + kind.slug() + " has been uploaded");
        }
        return new StoredImage(bytes, contentType);
    }

    // ------------------------------------------------------------------ util

    /**
     * Flushes so the entity carries post-write state before it is mapped.
     *
     * <p>{@code @UpdateTimestamp} is applied by Hibernate at flush, not at
     * setter time. Mapping a dirty-but-unflushed entity therefore returns the
     * PREVIOUS {@code updatedAt} — the response would report a save one behind,
     * and, because the brand-image URLs carry that timestamp as their cache
     * key, a freshly uploaded logo would come back under the old image's URL
     * and the browser would keep showing the picture it had cached.</p>
     */
    private Tenant persist(Tenant tenant) {
        return tenantRepository.saveAndFlush(tenant);
    }

    private void apply(Tenant tenant, CompanyImageKind kind, byte[] bytes, String contentType) {
        if (kind == CompanyImageKind.LOGO) {
            tenant.setLogo(bytes, contentType);
        } else {
            tenant.setCover(bytes, contentType);
        }
    }

    /**
     * The caller's tenant, or a refusal.
     *
     * <p>A null tenantId means a platform admin with no workspace of their own,
     * which is a 403 rather than a 404 — the request is well-formed, the caller
     * simply has no company profile to act on.</p>
     */
    private Tenant require(UUID tenantId) {
        if (tenantId == null) {
            throw new AccessDeniedException("A company profile requires a tenant-scoped account");
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    /** Trims, and treats blank as absent so "" and null cannot both mean empty. */
    private String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String lower(String value) {
        String trimmed = text(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Hard ceiling on a curated vocabulary — these are hand-maintained lists. */
    private static final int MAX_LIST_SIZE = 200;

    /**
     * Normalises a vocabulary: never null, trimmed, blanks dropped, duplicates
     * removed, original order kept. Returning a list rather than null is what
     * lets every reader skip the empty-versus-absent distinction.
     */
    private List<String> list(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> cleaned = values.stream()
                .map(this::text)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(MAX_LIST_SIZE)
                .toList();
        if (values.size() > MAX_LIST_SIZE) {
            throw new BusinessRuleException("A list may hold at most " + MAX_LIST_SIZE + " entries");
        }
        return cleaned;
    }

    /** Subdomains are case-insensitive identifiers: store and compare lowercase. */
    private String normalize(String subdomain) {
        return subdomain == null ? null : subdomain.trim().toLowerCase(Locale.ROOT);
    }
}
