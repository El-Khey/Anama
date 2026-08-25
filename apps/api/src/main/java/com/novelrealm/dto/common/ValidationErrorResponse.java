package com.novelrealm.dto.common;

import java.time.Instant;
import java.util.Map;

public record ValidationErrorResponse(int status, String message, Map<String, String> errors, Instant timestamp) {
    
    public ValidationErrorResponse(int status, String message, Map<String, String> errors) {
        this(status, message, errors, Instant.now());
    }
}