package com.talentpipe.job.service;

/**
 * The dashboard list's optional filters, as the query string carried them.
 * Each one left {@code null} or blank does not filter.
 *
 * @param status     {@code ACTIVE} for everything not archived, or one status name
 * @param assignedTo {@code me}, or a member's id: vacancies that person
 *                   recruits for, manages or is on the hiring team of
 * @param department the vacancy's department, matched whole, ignoring case
 */
public record VacancyListFilter(String status, String assignedTo, String department) {

    /** No filter at all: every vacancy the viewer may see. */
    public static VacancyListFilter none() {
        return new VacancyListFilter(null, null, null);
    }
}
