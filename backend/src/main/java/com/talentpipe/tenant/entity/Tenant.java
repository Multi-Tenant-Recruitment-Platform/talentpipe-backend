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
 * <p>The profile fields (branding, identity, locale, contact, location,
 * taxonomy) are updated through the Dashboard Settings page via
 * {@code PATCH /api/v1/tenant} and are readable by dashboard roles via
 * {@code GET /api/v1/tenant}. A curated subset is exposed publicly via
 * {@code GET /api/v1/public/companies/{subdomain}}.</p>
 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    // ---------------------------------------------------------------- identity

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Unique, URL-safe identifier used to resolve the tenant at login. */
    @Column(name = "subdomain", nullable = false, unique = true, length = 100)
    private String subdomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false, length = 30)
    private PlanTier planTier = PlanTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    // --------------------------------------------------------------- branding

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "tagline", length = 140)
    private String tagline;

    // ---------------------------------------------------------- company info

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "company_type", length = 100)
    private String companyType;

    @Column(name = "size", length = 100)
    private String size;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "culture", columnDefinition = "TEXT")
    private String culture;

    @Column(name = "mission", columnDefinition = "TEXT")
    private String mission;

    @Column(name = "vision", columnDefinition = "TEXT")
    private String vision;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    // ------------------------------------------------------------- localisation

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "language", length = 10)
    private String language;

    // --------------------------------------------------------------- contact

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "hr_email", length = 255)
    private String hrEmail;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "alternative_phone", length = 50)
    private String alternativePhone;

    @Column(name = "website", columnDefinition = "TEXT")
    private String website;

    @Column(name = "linkedin_url", columnDefinition = "TEXT")
    private String linkedinUrl;

    @Column(name = "facebook_url", columnDefinition = "TEXT")
    private String facebookUrl;

    @Column(name = "twitter_url", columnDefinition = "TEXT")
    private String twitterUrl;

    @Column(name = "instagram_url", columnDefinition = "TEXT")
    private String instagramUrl;

    // --------------------------------------------------------------- location

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "postal_code", length = 60)
    private String postalCode;

    @Column(name = "country", length = 120)
    private String country;

    // -------------------------------------------------------- list / taxonomy

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "values", columnDefinition = "TEXT[]")
    private List<String> values = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "benefits", columnDefinition = "TEXT[]")
    private List<String> benefits = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "work_modes", columnDefinition = "TEXT[]")
    private List<String> workModes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "office_locations", columnDefinition = "TEXT[]")
    private List<String> officeLocations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "departments", columnDefinition = "TEXT[]")
    private List<String> departments = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "teams", columnDefinition = "TEXT[]")
    private List<String> teams = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "business_units", columnDefinition = "TEXT[]")
    private List<String> businessUnits = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "employment_types", columnDefinition = "TEXT[]")
    private List<String> employmentTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "job_categories", columnDefinition = "TEXT[]")
    private List<String> jobCategories = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "job_families", columnDefinition = "TEXT[]")
    private List<String> jobFamilies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "job_levels", columnDefinition = "TEXT[]")
    private List<String> jobLevels = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "job_titles", columnDefinition = "TEXT[]")
    private List<String> jobTitles = new ArrayList<>();

    // ---------------------------------------------------------- constructors

    protected Tenant() {
        // for JPA
    }

    public Tenant(String name, String subdomain) {
        this.name = name;
        this.subdomain = subdomain;
    }

    // ------------------------------------------------------ identity getters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public PlanTier getPlanTier() {
        return planTier;
    }

    public TenantStatus getStatus() {
        return status;
    }

    // ----------------------------------------------------- branding getters/setters

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    // --------------------------------------------------- company info getters/setters

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
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

    // ----------------------------------------------- localisation getters/setters

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

    // ---------------------------------------------------- contact getters/setters

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

    // --------------------------------------------------- location getters/setters

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

    // ------------------------------------------------- list getters/setters

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values != null ? values : new ArrayList<>();
    }

    public List<String> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<String> benefits) {
        this.benefits = benefits != null ? benefits : new ArrayList<>();
    }

    public List<String> getWorkModes() {
        return workModes;
    }

    public void setWorkModes(List<String> workModes) {
        this.workModes = workModes != null ? workModes : new ArrayList<>();
    }

    public List<String> getOfficeLocations() {
        return officeLocations;
    }

    public void setOfficeLocations(List<String> officeLocations) {
        this.officeLocations = officeLocations != null ? officeLocations : new ArrayList<>();
    }

    public List<String> getDepartments() {
        return departments;
    }

    public void setDepartments(List<String> departments) {
        this.departments = departments != null ? departments : new ArrayList<>();
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams != null ? teams : new ArrayList<>();
    }

    public List<String> getBusinessUnits() {
        return businessUnits;
    }

    public void setBusinessUnits(List<String> businessUnits) {
        this.businessUnits = businessUnits != null ? businessUnits : new ArrayList<>();
    }

    public List<String> getEmploymentTypes() {
        return employmentTypes;
    }

    public void setEmploymentTypes(List<String> employmentTypes) {
        this.employmentTypes = employmentTypes != null ? employmentTypes : new ArrayList<>();
    }

    public List<String> getJobCategories() {
        return jobCategories;
    }

    public void setJobCategories(List<String> jobCategories) {
        this.jobCategories = jobCategories != null ? jobCategories : new ArrayList<>();
    }

    public List<String> getJobFamilies() {
        return jobFamilies;
    }

    public void setJobFamilies(List<String> jobFamilies) {
        this.jobFamilies = jobFamilies != null ? jobFamilies : new ArrayList<>();
    }

    public List<String> getJobLevels() {
        return jobLevels;
    }

    public void setJobLevels(List<String> jobLevels) {
        this.jobLevels = jobLevels != null ? jobLevels : new ArrayList<>();
    }

    public List<String> getJobTitles() {
        return jobTitles;
    }

    public void setJobTitles(List<String> jobTitles) {
        this.jobTitles = jobTitles != null ? jobTitles : new ArrayList<>();
    }
}
