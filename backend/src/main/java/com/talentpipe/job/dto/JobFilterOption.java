package com.talentpipe.job.dto;

/**
 * One choice in a public job-board filter, with how many published vacancies
 * it would return.
 *
 * @param value the value to send back as the {@code category} or
 *              {@code location} query parameter
 * @param count published vacancies currently matching it
 */
public record JobFilterOption(String value, long count) {
}
