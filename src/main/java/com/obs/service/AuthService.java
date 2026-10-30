package com.obs.service;

import com.obs.dao.UserDao;
import com.obs.db.Database;
import com.obs.exception.AuthenticationException;
import com.obs.exception.ConflictException;
import com.obs.model.Role;
import com.obs.model.SessionUser;
import com.obs.model.User;
import com.obs.util.PasswordHasher;
import com.obs.util.Validator;
import java.sql.SQLException;
import java.util.Optional;

public final class AuthService {

    /** Used when the email is unknown, so a failed login takes as long as a wrong password. */
    private static final String DUMMY_HASH = PasswordHasher.hash("timing-equaliser-1");

    private final Database db;
    private final UserDao users;

    public AuthService(Database db, UserDao users) {
        this.db = db;
        this.users = users;
    }

    public SessionUser register(String fullName, String email, String password, String confirm)
            throws SQLException {
        Validator v = new Validator();
        String name = v.text("fullName", fullName, 2, 80, true);
        String mail = v.email("email", email);
        String pass = v.password("password", password);
        v.check(pass == null || pass.equals(confirm), "confirmPassword", "Passwords do not match.");
        v.throwIfInvalid();

        return db.withConnection(c -> {
            if (users.emailExists(c, mail)) {
                throw new ConflictException("An account with this email already exists. Try signing in instead.");
            }
            int id = users.insert(c, name, mail, PasswordHasher.hash(pass), Role.CUSTOMER);
            return new SessionUser(id, name, mail, Role.CUSTOMER);
        });
    }

    public SessionUser login(String email, String password) throws SQLException {
        Validator v = new Validator();
        String mail = v.email("email", email);
        v.check(password != null && !password.isEmpty(), "password", "This field is required.");
        v.throwIfInvalid();

        Optional<User> user = db.withConnection(c -> users.findByEmail(c, mail));
        String hash = user.map(User::passwordHash).orElse(DUMMY_HASH);
        boolean ok = PasswordHasher.matches(password, hash);
        if (user.isEmpty() || !ok) {
            throw new AuthenticationException("That email and password don't match an account.");
        }
        return user.get().toSessionUser();
    }

    /** Creates the first admin account from config so a fresh database can be managed straight away. */
    public boolean ensureAdmin(String fullName, String email, String password) throws SQLException {
        return db.withConnection(c -> {
            if (users.adminExists(c) || users.emailExists(c, email)) {
                return false;
            }
            users.insert(c, fullName, email.toLowerCase(), PasswordHasher.hash(password), Role.ADMIN);
            return true;
        });
    }
}
