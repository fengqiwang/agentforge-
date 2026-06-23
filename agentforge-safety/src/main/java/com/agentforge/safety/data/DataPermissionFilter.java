package com.agentforge.safety.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Row-level data permission filter.
 * Currently passthrough — inject WHERE clauses per user role as needed.
 */
@Slf4j
@Component
public class DataPermissionFilter {

    /**
     * Apply row-level filters to query results based on user role.
     * Passthrough by default; override for RBAC enforcement.
     */
    public Object filter(Object data, Long userId) {
        log.debug("DataPermissionFilter: passthrough for userId={}", userId);
        return data;
    }
}
