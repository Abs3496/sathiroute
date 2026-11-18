package com.routemesh.shared.limits;

import java.time.Duration;

/** Fixed-window counter used for public token actions and authenticated API calls. */
public interface RateLimiter {
    boolean allow(String bucket, int limit, Duration window);
}
