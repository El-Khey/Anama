package com.novelrealm.dto.common;

import java.time.Instant;

public record ErrorResponse(int status, String message, Instant timestamp) {

    public ErrorResponse(int status, String message) {
        this(status, message, Instant.now()); // ajoute l'horodatage automatiquement
    }
}