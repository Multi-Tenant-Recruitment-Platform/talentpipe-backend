package com.talentpipe.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;

/**
 * Turns on placeholder expansion in security meta-annotations, which is what
 * makes {@link RequirePermission} work: its {@code {value}} placeholder is
 * replaced with the annotation's attribute before the expression is parsed.
 *
 * <p>Kept apart from {@link SecurityConfig} so controller slice tests, which
 * cannot load the full security configuration, can import just this.</p>
 *
 * <p>The bean is {@code static} so it is available before the method-security
 * infrastructure is created, as Spring Security requires.</p>
 */
@Configuration(proxyBeanMethods = false)
public class MethodSecurityConfig {

    private MethodSecurityConfig() {
        // configuration class with static bean definitions only
    }

    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }
}
