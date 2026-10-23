package com.talentpipe.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.talentpipe.common.exception.ErrorResponse.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for the error envelope: the original fields stay exactly as they
 * were, {@code code} is always present, and the optional parts appear only
 * when they carry something.
 */
class ErrorResponseTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void everyEnvelope_keepsTheOriginalFields_andAddsACode() {
        JsonNode body = json.valueToTree(ErrorResponse.of(HttpStatus.NOT_FOUND, "Vacancy not found", "/api/v1/jobs/x"));

        assertThat(fieldNames(body)).containsExactly("timestamp", "status", "error", "code", "message", "path");
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("error").asText()).isEqualTo("Not Found");
        assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("Vacancy not found");
    }

    @Test
    void fieldErrorsAndDetails_appearOnlyWhenSet() {
        ErrorResponse envelope = ErrorResponse.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "name: Give the role a name.", "/api/v1/roles")
                .withFieldErrors(List.of(new FieldErrorDetail("name", "Give the role a name.")))
                .withDetails(Map.of("hint", "x"));

        JsonNode body = json.valueToTree(envelope);

        assertThat(body.get("fieldErrors").get(0).get("field").asText()).isEqualTo("name");
        assertThat(body.get("fieldErrors").get(0).get("message").asText()).isEqualTo("Give the role a name.");
        assertThat(body.get("details").get("hint").asText()).isEqualTo("x");
    }

    @Test
    void eachStatus_hasASensibleDefaultCode() {
        assertThat(ErrorCode.defaultFor(HttpStatus.BAD_REQUEST)).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(ErrorCode.defaultFor(HttpStatus.UNAUTHORIZED)).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
        assertThat(ErrorCode.defaultFor(HttpStatus.FORBIDDEN)).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(ErrorCode.defaultFor(HttpStatus.CONFLICT)).isEqualTo(ErrorCode.CONFLICT);
        assertThat(ErrorCode.defaultFor(HttpStatus.UNPROCESSABLE_ENTITY)).isEqualTo(ErrorCode.BUSINESS_RULE);
        assertThat(ErrorCode.defaultFor(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(ErrorCode.defaultFor(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void unverifiedAndInvitedAccounts_carryDifferentCodes() {
        assertThat(new AccountNotVerifiedException("x").getCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_VERIFIED);
        assertThat(AccountNotVerifiedException.invitationPending("x").getCode())
                .isEqualTo(ErrorCode.INVITATION_NOT_ACCEPTED);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
