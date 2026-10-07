package com.hms.platform.config;

import java.util.regex.Pattern;

/** Keeps secrets that live in a URL path out of logs. */
public final class Redact {

    private static final Pattern MPESA_PATH = Pattern.compile("(/mpesa/)[^/?#\\s]+(/confirmation)");

    private Redact() {}

    /** {@code /v1/billing/mpesa/<secret>/confirmation} becomes {@code /v1/billing/mpesa/***\/confirmation}. */
    public static String path(String path) {
        return path == null ? null : MPESA_PATH.matcher(path).replaceAll("$1***$2");
    }
}
