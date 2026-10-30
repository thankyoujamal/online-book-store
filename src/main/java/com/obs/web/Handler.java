package com.obs.web;

@FunctionalInterface
public interface Handler {

    /** Returns a body to send as JSON with status 200, or a {@link Reply} for full control. */
    Object handle(Request request) throws Exception;
}
