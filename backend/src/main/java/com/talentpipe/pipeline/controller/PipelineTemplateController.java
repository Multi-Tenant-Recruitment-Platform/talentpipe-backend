package com.talentpipe.pipeline.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.service.PipelineTemplateService;
import com.talentpipe.security.Authorities;
import com.talentpipe.security.Permission;
import com.talentpipe.security.RequirePermission;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for recruitment pipeline templates.
 */
@RestController
@RequestMapping("/api/v1/pipeline-templates")
public class PipelineTemplateController {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Who may read templates: whoever manages them, and anyone whose role can
     * create vacancies, because a vacancy cannot be created without choosing a
     * template. Readers who cannot manage templates see active ones only (the
     * service hides archived ones). Every write needs
     * {@code pipeline.templates.manage}.
     */
    static final String CAN_READ_TEMPLATES = "hasAuthority('" + Permission.Keys.PIPELINE_TEMPLATES_MANAGE
            + "') or hasAuthority('" + Permission.Keys.JOBS_MANAGE + "')";

    private final PipelineTemplateService templateService;

    public PipelineTemplateController(PipelineTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permission.Keys.PIPELINE_TEMPLATES_MANAGE)
    public PipelineTemplateResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PipelineTemplateRequest request) {
        return templateService.create(principal.tenantId(), principal.id(), request);
    }

    @GetMapping
    @PreAuthorize(CAN_READ_TEMPLATES)
    public PageResponse<PipelineTemplateSummaryResponse> list(
            Authentication authentication,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = createPageable(page, size);
        Page<PipelineTemplateSummaryResponse> result = templateService.list(
                principal.tenantId(), search, includeArchived, managesTemplates(authentication), pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ_TEMPLATES)
    public PipelineTemplateResponse get(
            Authentication authentication,
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        return templateService.get(principal.tenantId(), id, managesTemplates(authentication));
    }

    @PutMapping("/{id}")
    @RequirePermission(Permission.Keys.PIPELINE_TEMPLATES_MANAGE)
    public PipelineTemplateResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody PipelineTemplateRequest request) {
        return templateService.update(principal.tenantId(), principal.id(), id, request);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(Permission.Keys.PIPELINE_TEMPLATES_MANAGE)
    public PipelineTemplateResponse archive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        return templateService.archive(principal.tenantId(), id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permission.Keys.PIPELINE_TEMPLATES_MANAGE)
    public PipelineTemplateResponse duplicate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        return templateService.duplicate(principal.tenantId(), principal.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permission.Keys.PIPELINE_TEMPLATES_MANAGE)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        templateService.delete(principal.tenantId(), id);
    }

    private Pageable createPageable(int page, int size) {
        int validatedPage = Math.max(0, page);
        int validatedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(validatedPage, validatedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Whether the caller manages templates right now, so also sees archived
     * ones. From the current permissions, not the role name in the token.
     */
    private static boolean managesTemplates(Authentication authentication) {
        return Authorities.has(authentication, Permission.PIPELINE_TEMPLATES_MANAGE);
    }
}
