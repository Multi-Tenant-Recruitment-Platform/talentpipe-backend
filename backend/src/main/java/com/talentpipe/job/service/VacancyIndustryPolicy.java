package com.talentpipe.job.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.tenant.dto.ProfileTaxonomy;
import com.talentpipe.tenant.service.TenantService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Which industry a vacancy is filed under. It must be one of its company's
 * industries (the main one plus up to three more, from the company profile),
 * so a company in IT cannot advertise under Healthcare.
 *
 * <p>Not required: a company that has not set an industry can still file
 * vacancies, which then have none. As soon as it sets one, new vacancies
 * default to it.</p>
 */
@Component
public class VacancyIndustryPolicy {

    private final TenantService tenantService;

    public VacancyIndustryPolicy(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * The industry for a vacancy being created or edited.
     *
     * @param requested what the request asked for; blank for "the default":
     *                  the current industry when editing, otherwise the
     *                  company's main industry
     * @param current   the vacancy's industry as stored, {@code null} for a
     *                  new vacancy. Kept even if the company has since
     *                  dropped it from its profile, so an unrelated edit
     *                  never fails because of it.
     * @return the canonical label, or {@code null} when the company has none
     * @throws BusinessRuleException (422) {@code requested} is not one of the
     *         company's industries
     */
    public String resolve(UUID tenantId, String requested, String current) {
        if (requested == null || requested.isBlank()) {
            return current != null ? current : mainIndustry(tenantId);
        }
        Optional<String> wanted = ProfileTaxonomy.industry(requested);
        if (wanted.isPresent() && wanted.get().equals(current)) {
            return current;
        }
        List<String> allowed = tenantService.industriesOf(tenantId);
        if (wanted.isEmpty() || !allowed.contains(wanted.get())) {
            throw new BusinessRuleException(allowed.isEmpty()
                    ? "Add your company's industry to its profile before choosing one for a vacancy."
                    : "Choose one of your company's industries: " + String.join(", ", allowed) + ".");
        }
        return wanted.get();
    }

    /**
     * The industry for a duplicate: the source's, if the company still works
     * in it, otherwise the company's main industry.
     */
    public String forDuplicate(UUID tenantId, String sourceIndustry) {
        List<String> allowed = tenantService.industriesOf(tenantId);
        if (sourceIndustry != null && allowed.contains(sourceIndustry)) {
            return sourceIndustry;
        }
        return allowed.isEmpty() ? null : allowed.get(0);
    }

    private String mainIndustry(UUID tenantId) {
        List<String> allowed = tenantService.industriesOf(tenantId);
        return allowed.isEmpty() ? null : allowed.get(0);
    }
}
