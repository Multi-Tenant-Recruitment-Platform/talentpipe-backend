package com.talentpipe.application.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * A list of strings stored as one JSON array.
 *
 * <p>A type of its own because Hibernate keeps one mapping per Java type, and
 * {@code List<String>} is already mapped as {@code TEXT[]} (on the vacancy). A
 * {@code List<String>} mapped as JSON anywhere takes that mapping over, and the
 * application then refuses to start, on the vacancy's columns.</p>
 */
record JsonStrings(@JsonValue List<String> values) {

    JsonStrings {
        values = List.copyOf(values);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static JsonStrings of(List<String> values) {
        return new JsonStrings(values);
    }

    static JsonStrings none() {
        return new JsonStrings(List.of());
    }
}
