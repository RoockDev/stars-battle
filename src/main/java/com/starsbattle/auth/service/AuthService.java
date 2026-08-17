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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Register/login (spec: "Registration and Login"). Register auto-assigns the
 * seeded "USER" role (must pre-exist — V2__roles.sql) and rejects duplicate
 * emails with a 400 via {@link BusinessRuleException}. Login rejects invalid
 * credentials with a single generic message regardless of whether the email
 * or the password was wrong, so it never leaks which field failed.
 */
@Service
public class AuthService {

    private static final String USER_ROLE_NAME = "USER";
    private static final String DUPLICATE_EMAIL_MESSAGE = "El email ya esta registrado";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales invalidas";

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
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException(DUPLICATE_EMAIL_MESSAGE);
        }
        Role userRole = roleRepository.findByName(USER_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol " + USER_ROLE_NAME + " no esta configurado (falta V2__roles.sql?)"));

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.addRole(userRole);
        user = userRepository.save(user);

        return issueAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        return issueAuthResponse(user);
    }

    private AuthResponse issueAuthResponse(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        String token = jwtIssuer.issue(user.getId(), user.getEmail(), roleNames);
        return new AuthResponse(token, UserSummary.from(user));
    }
}
