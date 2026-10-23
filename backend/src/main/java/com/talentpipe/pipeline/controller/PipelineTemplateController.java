package com.talentpipe.pipeline.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.service.PipelineTemplateService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final PipelineTemplateService templateService;

    public PipelineTemplateController(PipelineTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PipelineTemplateResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PipelineTemplateRequest request) {
        return templateService.create(principal.tenantId(), principal.id(), request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    public PageResponse<PipelineTemplateSummaryResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = createPageable(page, size);
        boolean isAdmin = isCompanyAdmin(principal);
        Page<PipelineTemplateSummaryResponse> result =
                templateService.list(principal.tenantId(), search, includeArchived, isAdmin, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    public PipelineTemplateResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        return templateService.get(principal.tenantId(), id, isCompanyAdmin(principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PipelineTemplateResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody PipelineTemplateRequest request) {
        return templateService.update(principal.tenantId(), principal.id(), id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PipelineTemplateResponse archive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        return templateService.archive(principal.tenantId(), id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PipelineTemplateResponse duplicate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id) {
        return templateService.duplicate(principal.tenantId(), principal.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
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

    private boolean isCompanyAdmin(UserPrincipal principal) {
        return "COMPANY_ADMIN".equalsIgnoreCase(principal.role());
    }
}
