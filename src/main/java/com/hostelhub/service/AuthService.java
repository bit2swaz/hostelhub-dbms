package com.hostelhub.service;

import com.hostelhub.dao.UserDao;
import com.hostelhub.dao.WardenDao;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.model.Role;
import com.hostelhub.util.PasswordHasher;
import com.hostelhub.util.Session;

/** FR-01: email + password login with role-based routing. */
public class AuthService {

    public static final String INVALID = "Invalid email or password.";
    // Verified against when the email is unknown, so both failures take the same time.
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");

    private final ConnectionFactory db;

    public AuthService(ConnectionFactory db) {
        this.db = db;
    }

    public Session login(String email, String password) {
        Validators.require(email != null && !email.isBlank() && password != null && !password.isEmpty(),
                "Enter your email and password.");
        return Tx.run(db, c -> {
            var user = UserDao.findByEmail(c, email.trim());
            boolean ok = PasswordHasher.verify(password, user.map(UserDao.Credentials::passwordHash).orElse(DUMMY_HASH));
            if (user.isEmpty() || !ok) {
                throw new ServiceException(INVALID);
            }
            var u = user.get();
            Long blockId = u.role() == Role.WARDEN ? WardenDao.blockIdFor(c, u.userId()).orElse(null) : null;
            return new Session(u.userId(), u.fullName(), u.email(), u.role(), blockId);
        });
    }
}
