package com.boxy.boxy.core.realtime;

/**
 * The topic names, in one place. Every name embeds the tenant scope it belongs to, which is what
 * {@link StompAuthChannelInterceptor} authorizes a SUBSCRIBE against — so a new topic must follow
 * one of the shapes that interceptor recognizes (or the interceptor must learn the new shape),
 * otherwise no client will ever be allowed to listen to it.
 */
public final class RealtimeTopics {

    private RealtimeTopics() {
    }

    /** Stock levels changing anywhere in the company (sales, transfers, receipts, adjustments). */
    public static String stock(Long companyId) {
        return "/topic/company." + companyId + ".stock";
    }

    /** Transfers moving through their lifecycle, company-wide (origin and destination differ by branch). */
    public static String transfers(Long companyId) {
        return "/topic/company." + companyId + ".transfers";
    }

    /** Sales, voids and payments of one branch. */
    public static String sales(Long companyId, Long branchId) {
        return "/topic/company." + companyId + ".branch." + branchId + ".sales";
    }
}
