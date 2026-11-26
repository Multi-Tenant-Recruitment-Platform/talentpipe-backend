package com.talentpipe.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiServicePropertiesTest {

    @Test
    void constructor_suppliesDefaultsWhenBlankOrNonPositive() {
        AiServiceProperties defaultsFromNull = new AiServiceProperties(null, 0);
        AiServiceProperties defaultsFromBlank = new AiServiceProperties("   ", -5);

        assertThat(defaultsFromNull.baseUrl()).isEqualTo("http://ai-service:8000");
        assertThat(defaultsFromNull.timeoutSeconds()).isEqualTo(30);
        assertThat(defaultsFromBlank.baseUrl()).isEqualTo("http://ai-service:8000");
        assertThat(defaultsFromBlank.timeoutSeconds()).isEqualTo(30);
    }

    @Test
    void constructor_preservesExplicitValues() {
        AiServiceProperties explicit = new AiServiceProperties("http://localhost:8000", 15);

        assertThat(explicit.baseUrl()).isEqualTo("http://localhost:8000");
        assertThat(explicit.timeoutSeconds()).isEqualTo(15);
    }
}
