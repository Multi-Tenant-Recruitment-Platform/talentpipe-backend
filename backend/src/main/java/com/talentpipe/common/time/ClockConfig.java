package com.talentpipe.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's source of "now".
 *
 * <p>Code whose behavior depends on the date — whether a deadline has passed,
 * the moment a vacancy was published — takes a {@link Clock} instead of
 * calling {@code Instant.now()} itself. In production that is this UTC system
 * clock; a unit test passes {@code Clock.fixed(...)} and gets the same answer
 * on every run, on any day.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
