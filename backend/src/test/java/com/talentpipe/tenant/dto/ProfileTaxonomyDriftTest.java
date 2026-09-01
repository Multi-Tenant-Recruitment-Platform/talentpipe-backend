package com.talentpipe.tenant.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.common.util.AllowedValues;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code @AllowedValues} annotation values must be compile-time constants, so
 * {@link UpdateCompanyProfileRequest} necessarily repeats each checkbox
 * option set as an array literal rather than referencing {@link
 * ProfileTaxonomy} directly. {@link ProfileNormalizer} (via {@code
 * TenantService}) canonicalizes accepted values to {@code ProfileTaxonomy}'s
 * casing — if the two copies drift, the validator would silently accept an
 * option the canonicalizer doesn't recognise, or vice versa.
 *
 * <p>This test walks the record's components by reflection and asserts each
 * {@code @AllowedValues} array is identical, in order, to its {@code
 * ProfileTaxonomy} counterpart — so any future edit to one without the other
 * fails the build instead of drifting silently.</p>
 */
class ProfileTaxonomyDriftTest {

    private static final Map<String, List<String>> EXPECTED = Map.of(
            "workModes", ProfileTaxonomy.WORK_MODES,
            "benefits", ProfileTaxonomy.BENEFITS,
            "employmentTypes", ProfileTaxonomy.EMPLOYMENT_TYPES,
            "jobLevels", ProfileTaxonomy.JOB_LEVELS);

    @Test
    void everyAllowedValuesFieldMatchesItsProfileTaxonomyCounterpart() throws Exception {
        int checked = 0;
        for (RecordComponent component : UpdateCompanyProfileRequest.class.getRecordComponents()) {
            // @AllowedValues targets FIELD/PARAMETER, not METHOD, so on a
            // record it lands on the synthetic private field, not the
            // accessor — read it from there.
            AllowedValues annotation = UpdateCompanyProfileRequest.class
                    .getDeclaredField(component.getName())
                    .getAnnotation(AllowedValues.class);
            if (annotation == null) {
                continue;
            }
            List<String> expected = EXPECTED.get(component.getName());
            assertThat(expected)
                    .as("no ProfileTaxonomy entry registered for @AllowedValues field '%s' — "
                            + "add one to this test's EXPECTED map", component.getName())
                    .isNotNull();
            assertThat(Arrays.asList(annotation.value()))
                    .as("@AllowedValues on '%s' has drifted from ProfileTaxonomy", component.getName())
                    .containsExactlyElementsOf(expected);
            checked++;
        }
        assertThat(checked)
                .as("expected to find all four checkbox-driven fields via reflection")
                .isEqualTo(EXPECTED.size());
    }
}
