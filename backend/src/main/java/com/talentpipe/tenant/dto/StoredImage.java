package com.talentpipe.tenant.dto;

/** An image as stored, ready to be written to an HTTP response. */
public record StoredImage(byte[] bytes, String contentType) {
}
