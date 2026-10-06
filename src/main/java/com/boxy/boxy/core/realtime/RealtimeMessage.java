package com.boxy.boxy.core.realtime;

import java.time.Instant;

/**
 * The single envelope every server-pushed event travels in (mirrored by the frontend's
 * {@code RealtimeMessage<T>}). {@code type} discriminates the event within a topic and
 * {@code occurredAt} is an ISO-8601 string built here rather than left to Jackson's date settings,
 * so the wire format never depends on how a particular {@code ObjectMapper} is configured.
 * <p>
 * Payloads are deliberately minimal — ids, quantities, status — and carry no personal data. A
 * screen that needs more asks the REST API, which is where authorization lives.
 */
public record RealtimeMessage<T>(String type, String occurredAt, T data) {

    public static <T> RealtimeMessage<T> of(String type, T data) {
        return new RealtimeMessage<>(type, Instant.now().toString(), data);
    }
}
