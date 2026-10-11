package com.hostelhub.util;

import com.hostelhub.model.Role;

/**
 * The logged-in user. Services take a Session explicitly (so tests can act as any user);
 * the UI keeps the current one in {@link #current()}.
 */
public record Session(long userId, String fullName, String email, Role role, Long blockId) {

    private static volatile Session current;

    public static Session current() {
        return current;
    }

    public static void start(Session session) {
        current = session;
    }

    public static void end() {
        current = null;
    }

    public boolean isWarden() {
        return role == Role.WARDEN;
    }
}
