package com.talentpipe.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtractedCvResultTest {

    @Test
    void printingIt_neverPrintsTheCv() {
        ExtractedCvResult result = new ExtractedCvResult("Kasun Perera, backend engineer", 30, 2, false);

        assertThat(result.toString())
                .doesNotContain("Kasun")
                .contains("charCount=30", "pageCount=2", "emptyOrScanned=false");
        assertThat(result.text()).isEqualTo("Kasun Perera, backend engineer");
    }
}
