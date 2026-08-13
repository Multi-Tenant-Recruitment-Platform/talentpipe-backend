package com.talentpipe.tenant.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A customer company. Root of all tenant-scoped data on the platform.
 *
 * <p>Module boundary note: this entity is private to the tenant module. Other
 * modules reference tenants by {@code UUID} and talk to {@code TenantService},
 * never to this class.</p>
 *
 * <p>Identity fields — {@code subdomain}, {@code planTier}, {@code status} —
 * deliberately expose no setter. The subdomain is the workspace's address and
 * the login key; plan and status are billing/lifecycle state. None of the three
 * is the company admin's to change from a profile screen, so the absence of a
 * setter is the enforcement, not a convention someone must remember.</p>
 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Unique, URL-safe identifier used to resolve the tenant at login. */
    @Column(name = "subdomain", nullable = false, unique = true, length = 100)
    private String subdomain;

    @Column(name = "industry", length = 100)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 30)
    private PlanTier planTier = PlanTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    // --- Identity and positioning -------------------------------------------

    @Column(name = "tagline", length = 255)
    private String tagline;

    @Column(name = "company_type", length = 100)
    private String companyType;

    @Column(name = "company_size", length = 50)
    private String size;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    // --- Narrative ----------------------------------------------------------

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "culture", columnDefinition = "text")
    private String culture;

    @Column(name = "mission", columnDefinition = "text")
    private String mission;

    @Column(name = "vision", columnDefinition = "text")
    private String vision;

    // --- Operating defaults -------------------------------------------------

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "language", length = 50)
    private String language;

    // --- Contact ------------------------------------------------------------

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "hr_email", length = 255)
    private String hrEmail;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "alternative_phone", length = 50)
    private String alternativePhone;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Column(name = "twitter_url", length = 255)
    private String twitterUrl;

    @Column(name = "instagram_url", length = 255)
    private String instagramUrl;

    // --- Location -----------------------------------------------------------

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    // --- Curated vocabularies (JSONB) ---------------------------------------
    // Initialised to empty lists, never null, so callers never null-check a
    // collection and the mapper can hand them straight to the DTO.

    /** Stored as core_values: VALUES is reserved in SQL. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "core_values", nullable = false)
    private List<String> values = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", nullable = false)
    private List<String> benefits = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "work_modes", nullable = false)
    private List<String> workModes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "office_locations", nullable = false)
    private List<String> officeLocations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "departments", nullable = false)
    private List<String> departments = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teams", nullable = false)
    private List<String> teams = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_units", nullable = false)
    private List<String> businessUnits = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "employment_types", nullable = false)
    private List<String> employmentTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_categories", nullable = false)
    private List<String> jobCategories = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_families", nullable = false)
    private List<String> jobFamilies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_levels", nullable = false)
    private List<String> jobLevels = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_titles", nullable = false)
    private List<String> jobTitles = new ArrayList<>();

    // --- Branding -----------------------------------------------------------

    @Column(name = "logo_image")
    private byte[] logoImage;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "cover_image")
    private byte[] coverImage;

    @Column(name = "cover_content_type", length = 100)
    private String coverContentType;

    protected Tenant() {
        // for JPA
    }

    public Tenant(String name, String subdomain) {
        this.name = name;
        this.subdomain = subdomain;
    }

    // --- Read-only identity -------------------------------------------------

    public String getSubdomain() {
        return subdomain;
    }

    public PlanTier getPlanTier() {
        return planTier;
    }

    public TenantStatus getStatus() {
        return status;
    }

    // --- Profile ------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getCompanyType() {
        return companyType;
    }

    public void setCompanyType(String companyType) {
        this.companyType = companyType;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Integer getEmployeeCount() {
        return employeeCount;
    }

    public void setEmployeeCount(Integer employeeCount) {
        this.employeeCount = employeeCount;
    }

    public Integer getFoundedYear() {
        return foundedYear;
    }

    public void setFoundedYear(Integer foundedYear) {
        this.foundedYear = foundedYear;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCulture() {
        return culture;
    }

    public void setCulture(String culture) {
        this.culture = culture;
    }

    public String getMission() {
        return mission;
    }

    public void setMission(String mission) {
        this.mission = mission;
    }

    public String getVision() {
        return vision;
    }

    public void setVision(String vision) {
        this.vision = vision;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHrEmail() {
        return hrEmail;
    }

    public void setHrEmail(String hrEmail) {
        this.hrEmail = hrEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAlternativePhone() {
        return alternativePhone;
    }

    public void setAlternativePhone(String alternativePhone) {
        this.alternativePhone = alternativePhone;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getFacebookUrl() {
        return facebookUrl;
    }

    public void setFacebookUrl(String facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

    public String getTwitterUrl() {
        return twitterUrl;
    }

    public void setTwitterUrl(String twitterUrl) {
        this.twitterUrl = twitterUrl;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public List<String> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<String> benefits) {
        this.benefits = benefits;
    }

    public List<String> getWorkModes() {
        return workModes;
    }

    public void setWorkModes(List<String> workModes) {
        this.workModes = workModes;
    }

    public List<String> getOfficeLocations() {
        return officeLocations;
    }

    public void setOfficeLocations(List<String> officeLocations) {
        this.officeLocations = officeLocations;
    }

    public List<String> getDepartments() {
        return departments;
    }

    public void setDepartments(List<String> departments) {
        this.departments = departments;
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams;
    }

    public List<String> getBusinessUnits() {
        return businessUnits;
    }

    public void setBusinessUnits(List<String> businessUnits) {
        this.businessUnits = businessUnits;
    }

    public List<String> getEmploymentTypes() {
        return employmentTypes;
    }

    public void setEmploymentTypes(List<String> employmentTypes) {
        this.employmentTypes = employmentTypes;
    }

    public List<String> getJobCategories() {
        return jobCategories;
    }

    public void setJobCategories(List<String> jobCategories) {
        this.jobCategories = jobCategories;
    }

    public List<String> getJobFamilies() {
        return jobFamilies;
    }

    public void setJobFamilies(List<String> jobFamilies) {
        this.jobFamilies = jobFamilies;
    }

    public List<String> getJobLevels() {
        return jobLevels;
    }

    public void setJobLevels(List<String> jobLevels) {
        this.jobLevels = jobLevels;
    }

    public List<String> getJobTitles() {
        return jobTitles;
    }

    public void setJobTitles(List<String> jobTitles) {
        this.jobTitles = jobTitles;
    }

    // --- Branding -----------------------------------------------------------

    public byte[] getLogoImage() {
        return logoImage;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public byte[] getCoverImage() {
        return coverImage;
    }

    public String getCoverContentType() {
        return coverContentType;
    }

    /** Bytes and content type move together — see the DB check constraints. */
    public void setLogo(byte[] image, String contentType) {
        this.logoImage = image;
        this.logoContentType = contentType;
    }

    public void setCover(byte[] image, String contentType) {
        this.coverImage = image;
        this.coverContentType = contentType;
    }
}
