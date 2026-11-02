package com.obs.web;

/** A file response. inline=true lets the browser show it; false makes it save as a download. */
public record Download(String fileName, String contentType, byte[] bytes, boolean inline) {
}
