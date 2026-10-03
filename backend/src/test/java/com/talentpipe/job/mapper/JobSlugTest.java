package com.talentpipe.job.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link JobSlug}: building a vacancy's public URL key and reading the id back out. */
class JobSlugTest {

    private static final UUID ID = UUID.fromString("8f0c2c1e-4b7a-4d2e-9c3a-1b2c3d4e5f60");
    private static final String COMPACT_ID = "8f0c2c1e4b7a4d2e9c3a1b2c3d4e5f60";

    // ---------------------------------------------------------------- building

    @Test
    void of_joinsTitleCompanyAndCompactId() {
        assertThat(JobSlug.of("Senior Backend Engineer", "Acme", ID))
                .isEqualTo("senior-backend-engineer-acme-" + COMPACT_ID);
    }

    @Test
    void of_collapsesPunctuationAndSpacingIntoSingleHyphens() {
        assertThat(JobSlug.of("  Backend Engineer (Java / Spring Boot)  ", "Northwind & Co.", ID))
                .isEqualTo("backend-engineer-java-spring-boot-northwind-co-" + COMPACT_ID);
    }

    @Test
    void of_foldsAccentsToPlainLetters() {
        assertThat(JobSlug.of("Ingénieur Sénior", "Café Zürich", ID))
                .isEqualTo("ingenieur-senior-cafe-zurich-" + COMPACT_ID);
    }

    @Test
    void of_withNoUsableWords_isTheBareId() {
        assertThat(JobSlug.of("", "", ID)).isEqualTo(COMPACT_ID);
        assertThat(JobSlug.of(null, null, ID)).isEqualTo(COMPACT_ID);
        assertThat(JobSlug.of("සොෆ්ට්වෙයා", "+++", ID)).isEqualTo(COMPACT_ID);
    }

    @Test
    void of_capsTheReadablePart_cuttingAtAWordBoundary() {
        String longTitle = "Principal Distinguished Senior Staff Software Engineering Manager "
                + "for Platform Reliability and Developer Experience";

        String slug = JobSlug.of(longTitle, "Acme", ID);
        String words = slug.substring(0, slug.length() - COMPACT_ID.length() - 1);

        assertThat(words).hasSizeLessThanOrEqualTo(80).doesNotEndWith("-");
        assertThat(longTitle.toLowerCase().replace(' ', '-')).startsWith(words);
    }

    // --------------------------------------------------------------- resolving

    @Test
    void resolveId_readsTheIdBackFromASlug() {
        assertThat(JobSlug.resolveId(JobSlug.of("Senior Backend Engineer", "Acme", ID))).contains(ID);
    }

    @Test
    void resolveId_ignoresTheWords_soALinkSharedBeforeATitleEditStillResolves() {
        assertThat(JobSlug.resolveId("the-old-title-before-it-was-renamed-" + COMPACT_ID)).contains(ID);
    }

    @Test
    void resolveId_acceptsTheBareCompactId() {
        assertThat(JobSlug.resolveId(COMPACT_ID)).contains(ID);
    }

    @Test
    void resolveId_acceptsAPlainUuid() {
        assertThat(JobSlug.resolveId(ID.toString())).contains(ID);
    }

    @Test
    void resolveId_isCaseInsensitive() {
        assertThat(JobSlug.resolveId("Senior-Engineer-" + COMPACT_ID.toUpperCase())).contains(ID);
        assertThat(JobSlug.resolveId(ID.toString().toUpperCase())).contains(ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "senior-backend-engineer-acme",            // no id at all
            "filters",
            "senior-engineer-8f0c2c1e4b7a4d2e",        // id too short
            "8f0c2c1e4b7a4d2e9c3a1b2c3d4e5f60-extra",  // id not at the end
            "x8f0c2c1e4b7a4d2e9c3a1b2c3d4e5f60",       // 32 hex not on a segment boundary
            "senior-engineer-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
            "../../etc/passwd"
    })
    void resolveId_returnsEmptyForAnythingWithoutAWellFormedId(String input) {
        assertThat(JobSlug.resolveId(input)).isEmpty();
    }
}
