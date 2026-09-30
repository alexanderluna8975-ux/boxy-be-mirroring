package com.boxy.boxy.core.security;

/**
 * Turns a raw {@code User-Agent} header into a short human label like "Chrome en Windows" — used
 * in audit-log entries and new-device alerts so a user/admin can recognize a device without
 * reading a raw UA string. Deliberately a small regex table instead of a UA-parsing library: only
 * a handful of browsers/OSes actually show up in practice, and getting an unusual one slightly
 * wrong (falling back to "Navegador desconocido") is harmless here.
 */
public final class UserAgentSummary {

    private UserAgentSummary() {
    }

    public static String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Dispositivo desconocido";
        }

        return browserOf(userAgent) + " en " + osOf(userAgent);
    }

    private static String browserOf(String ua) {
        // Order matters: Edge/OPR/SamsungBrowser also contain "Chrome" or "Safari" in their UA,
        // so the more specific tokens must be checked first.
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("CriOS/") || ua.contains("Chrome/") || ua.contains("Chromium/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/") && ua.contains("Version/")) {
            return "Safari";
        }
        return "Navegador desconocido";
    }

    private static String osOf(String ua) {
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") && !ua.contains("iPhone") && !ua.contains("iPad")) {
            return "macOS";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "un sistema desconocido";
    }
}
