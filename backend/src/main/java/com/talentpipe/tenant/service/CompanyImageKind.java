package com.talentpipe.tenant.service;

/** The two brand images a company profile carries. */
public enum CompanyImageKind {

    /** Square-ish avatar shown in the sidebar and on job cards. */
    LOGO(2 * 1024 * 1024),

    /** Wide banner on the public company page; allowed to be heavier. */
    COVER(5 * 1024 * 1024);

    private final int maxBytes;

    CompanyImageKind(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int maxBytes() {
        return maxBytes;
    }

    /** Lowercase name used in URLs ('logo', 'cover'). */
    public String slug() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
