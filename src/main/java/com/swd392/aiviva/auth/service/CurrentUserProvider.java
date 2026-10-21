package com.swd392.aiviva.auth.service;

import java.util.UUID;

/** Public identity contract for other modules; user persistence stays owned by auth/user. */
public interface CurrentUserProvider {
    UUID currentUserId();
    boolean isAdmin();
}
