package com.talentpipe.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Build-time guards on how endpoints are authorized. Both rules are easy to
 * break silently and expensive to discover in production:
 *
 * <ol>
 *   <li>Every {@link RequirePermission} names a permission that exists. A typo
 *       would compile and produce an endpoint no role can ever call.</li>
 *   <li>Every endpoint outside the public and authentication paths says who
 *       may call it. Without an annotation, "authenticated" would be the only
 *       check — a candidate's token could call a company endpoint.</li>
 * </ol>
 */
class EndpointAuthorizationDriftTest {

    /** Paths that are deliberately open, or only need a valid token (/auth/me, /auth/logout). */
    private static final List<String> UNGUARDED_PREFIXES = List.of("/api/v1/public", "/api/v1/auth");

    @Test
    void everyRequirePermissionNamesACataloguedPermission() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            check(controller, controller.getSimpleName(), problems);
            for (Method method : controller.getDeclaredMethods()) {
                check(method, controller.getSimpleName() + "." + method.getName(), problems);
            }
        }
        assertThat(problems).as("@RequirePermission values not in the Permission catalogue").isEmpty();
    }

    @Test
    void everyCompanyEndpointDeclaresWhoMayCallIt() throws Exception {
        List<String> unguarded = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            String basePath = basePath(controller);
            if (UNGUARDED_PREFIXES.stream().anyMatch(basePath::startsWith)) {
                continue;
            }
            boolean classGuarded = isGuarded(controller);
            for (Method method : controller.getDeclaredMethods()) {
                if (isHandler(method) && !classGuarded && !isGuarded(method)) {
                    unguarded.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertThat(unguarded)
                .as("endpoints with no @RequirePermission, @RequireCompanyMember or @PreAuthorize")
                .isEmpty();
    }

    @Test
    void theScanActuallyFindsTheControllers() throws Exception {
        assertThat(controllers()).extracting(Class::getSimpleName)
                .contains("JobVacancyController", "TeamController", "RoleController", "TenantController");
    }

    // ----------------------------------------------------------------- helpers

    private static void check(AnnotatedElement element, String where, List<String> problems) {
        RequirePermission annotation = element.getAnnotation(RequirePermission.class);
        if (annotation != null && Permission.fromKey(annotation.value()).isEmpty()) {
            problems.add(where + " -> '" + annotation.value() + "'");
        }
    }

    private static boolean isGuarded(AnnotatedElement element) {
        return AnnotatedElementUtils.hasAnnotation(element, PreAuthorize.class);
    }

    private static boolean isHandler(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private static String basePath(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        return mapping == null || mapping.path().length == 0 ? "" : mapping.path()[0];
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.talentpipe")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            // Production endpoints only: test sources declare probe controllers of their own.
            if (type.getProtectionDomain().getCodeSource().getLocation().toString().contains("test-classes")) {
                continue;
            }
            // Exception handlers are not endpoints.
            if (!type.isAnnotationPresent(RestControllerAdvice.class)) {
                found.add(type);
            }
        }
        return found;
    }
}
