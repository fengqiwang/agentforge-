package com.agentforge.safety.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simple data masker for PII (phone numbers, ID cards, bank cards).
 * Extend with field-level rules as needed.
 */
@Slf4j
@Component
public class DataMasker {

    /**
     * Mask the middle portion of a sensitive string.
     * e.g. "13812345678" → "138****5678"
     */
    public String mask(String data) {
        if (data == null || data.isBlank()) return data;
        if (data.length() <= 4) return "****";
        int show = Math.max(3, data.length() / 4);
        return data.substring(0, show) + "****" + data.substring(data.length() - show);
    }
}
