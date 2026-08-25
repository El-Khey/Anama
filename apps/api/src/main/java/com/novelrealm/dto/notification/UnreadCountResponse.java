package com.novelrealm.dto.notification;

/** Le badge de la cloche (issue #45, §3) : juste le nombre de non-lues. */
public record UnreadCountResponse(long count) {}
