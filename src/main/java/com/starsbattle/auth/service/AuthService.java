package com.starsbattle.auth.service;

import com.starsbattle.auth.dto.AuthResponse;
import com.starsbattle.auth.dto.LoginRequest;
import com.starsbattle.auth.dto.RegisterRequest;
import com.starsbattle.auth.dto.UserSummary;
import com.starsbattle.common.exception.BusinessRuleException;
import com.starsbattle.users.domain.Role;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.RoleRepository;
import com.starsbattle.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Register/login (spec: "Registration and Login"). Register auto-assigns the
 * seeded "USER" role (must pre-exist — V2__roles.sql) and rejects duplicate
 * emails with a 400 via {@link BusinessRuleException} — both on the fast-path
 * {@code existsByEmail} pre-check and, as a safety net for the check-then-act
 * race between two concurrent registrations, on the unique-constraint
 * violation the second {@code save()} can still hit. Login rejects invalid
 * credentials with a single generic message regardless of whether the email
 * or the password was wrong, so it never leaks which field failed. Emails
 * are normalized (trimmed + lowercased) before any check, lookup, or
 * persistence so that e.g. 'User@Example.com' and 'user@example.com' are
 * treated as the same identity.
 */
@Service
public class AuthService {

    private static final String USER_ROLE_NAME = "USER";
    private static final String DUPLICATE_EMAIL_MESSAGE = "El email ya esta registrado";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException(DUPLICATE_EMAIL_MESSAGE);
        }
        Role userRole = roleRepository.findByName(USER_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol " + USER_ROLE_NAME + " no esta configurado (falta V2__roles.sql?)"));

        User user = new User(email, passwordEncoder.encode(request.password()));
        user.addRole(userRole);
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Safety net for the existsByEmail()-then-save() race: two concurrent
            // registrations for the same email can both pass the pre-check above,
            // so the DB's uq_users_email constraint is the actual source of truth.
            throw new BusinessRuleException(DUPLICATE_EMAIL_MESSAGE);
        }

        return issueAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }

        return issueAuthResponse(user);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse issueAuthResponse(User user) {
        List<String> roleNames = sortedRoleNames(user);
        String token = jwtIssuer.issue(user.getId(), user.getEmail(), roleNames);
        return new AuthResponse(token, UserSummary.from(user, roleNames));
    }

    private static List<String> sortedRoleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted(Comparator.naturalOrder()).toList();
    }
}
