package com.starsbattle.auth.service;

import com.starsbattle.auth.dto.AuthResponse;
import com.starsbattle.auth.dto.LoginRequest;
import com.starsbattle.auth.dto.RegisterRequest;
import com.starsbattle.common.exception.BusinessRuleException;
import com.starsbattle.users.domain.Role;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.RoleRepository;
import com.starsbattle.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repositories/collaborators, no Spring context — fast
 * feedback on AuthService's business rules (spec: "Registration and Login").
 * The full HTTP + real-DB + real-filter-chain flow is covered separately by
 * {@code AuthRegisterLoginIT}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtIssuer jwtIssuer;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, jwtIssuer);
    }

    @Test
    void registerAssignsUserRoleHashesPasswordAndReturnsTokenPlusSummary() {
        when(userRepository.existsByEmail("luke@batalla.com")).thenReturn(false);
        Role userRole = new Role("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("force123")).thenReturn("hashed-force123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtIssuer.issue(any(), eq("luke@batalla.com"), anyList())).thenReturn("signed-jwt-token");

        AuthResponse response = authService.register(new RegisterRequest("luke@batalla.com", "force123"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-force123");
        assertThat(savedUser.getValue().getRoles()).contains(userRole);

        assertThat(response.accessToken()).isEqualTo("signed-jwt-token");
        assertThat(response.user().email()).isEqualTo("luke@batalla.com");
        assertThat(response.user().roles()).containsExactly("USER");
    }

    @Test
    void registerRejectsDuplicateEmailWithBusinessRuleException() {
        when(userRepository.existsByEmail("duplicate@batalla.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("duplicate@batalla.com", "force123")))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void loginWithCorrectCredentialsReturnsTokenPlusSummary() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(9L);
        when(user.getEmail()).thenReturn("han@batalla.com");
        when(user.getPasswordHash()).thenReturn("hashed-solo123");
        when(user.getRoles()).thenReturn(java.util.Set.of(new Role("USER")));
        when(user.getLevel()).thenReturn(1);
        when(user.getXp()).thenReturn(0);
        when(user.getWins()).thenReturn(0);
        when(user.getLosses()).thenReturn(0);

        when(userRepository.findByEmail("han@batalla.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("solo123", "hashed-solo123")).thenReturn(true);
        when(jwtIssuer.issue(eq(9L), eq("han@batalla.com"), anyList())).thenReturn("another-jwt-token");

        AuthResponse response = authService.login(new LoginRequest("han@batalla.com", "solo123"));

        assertThat(response.accessToken()).isEqualTo("another-jwt-token");
        assertThat(response.user().id()).isEqualTo(9L);
    }

    @Test
    void loginWithUnknownEmailThrowsGenericBadCredentials() {
        when(userRepository.findByEmail("ghost@batalla.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@batalla.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void loginWithWrongPasswordThrowsGenericBadCredentials() {
        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn("hashed-real-password");
        when(userRepository.findByEmail("leia@batalla.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-real-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("leia@batalla.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void registerFailsFastWhenUserRoleIsNotSeeded() {
        when(userRepository.existsByEmail("new@batalla.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(new RegisterRequest("new@batalla.com", "force123")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerTranslatesUniqueConstraintRaceIntoBusinessRuleException() {
        // existsByEmail() misses because a concurrent request wins the race and
        // commits first — the DB's uq_users_email constraint is what actually
        // catches the duplicate, via DataIntegrityViolationException on save().
        when(userRepository.existsByEmail("racer@batalla.com")).thenReturn(false);
        Role userRole = new Role("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("force123")).thenReturn("hashed-force123");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("racer@batalla.com", "force123")))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void registerNormalizesEmailToTrimmedLowercaseEverywhere() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        Role userRole = new Role("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("force123")).thenReturn("hashed-force123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtIssuer.issue(any(), eq("user@example.com"), anyList())).thenReturn("signed-jwt-token");

        AuthResponse response = authService.register(new RegisterRequest("  User@Example.com  ", "force123"));

        verify(userRepository).existsByEmail("user@example.com");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void loginNormalizesEmailToTrimmedLowercaseBeforeLookup() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(9L);
        when(user.getEmail()).thenReturn("han@batalla.com");
        when(user.getPasswordHash()).thenReturn("hashed-solo123");
        when(user.getRoles()).thenReturn(Set.of(new Role("USER")));
        when(user.getLevel()).thenReturn(1);
        when(user.getXp()).thenReturn(0);
        when(user.getWins()).thenReturn(0);
        when(user.getLosses()).thenReturn(0);

        when(userRepository.findByEmail("han@batalla.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("solo123", "hashed-solo123")).thenReturn(true);
        when(jwtIssuer.issue(eq(9L), eq("han@batalla.com"), anyList())).thenReturn("another-jwt-token");

        AuthResponse response = authService.login(new LoginRequest("  HAN@Batalla.com ", "solo123"));

        verify(userRepository).findByEmail("han@batalla.com");
        assertThat(response.accessToken()).isEqualTo("another-jwt-token");
    }

    @SuppressWarnings("unchecked")
    @Test
    void jwtRolesClaimAndUserSummaryRolesAreSortedInTheSameOrder() {
        Set<Role> unsortedRoles = new LinkedHashSet<>();
        unsortedRoles.add(new Role("USER"));
        unsortedRoles.add(new Role("ADMIN"));
        unsortedRoles.add(new Role("MODERATOR"));

        when(userRepository.existsByEmail("multi@batalla.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(new Role("USER")));
        when(passwordEncoder.encode("force123")).thenReturn("hashed-force123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User persisted = mock(User.class);
            when(persisted.getId()).thenReturn(1L);
            when(persisted.getEmail()).thenReturn("multi@batalla.com");
            when(persisted.getRoles()).thenReturn(unsortedRoles);
            when(persisted.getLevel()).thenReturn(1);
            when(persisted.getXp()).thenReturn(0);
            when(persisted.getWins()).thenReturn(0);
            when(persisted.getLosses()).thenReturn(0);
            return persisted;
        });

        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        when(jwtIssuer.issue(any(), any(), rolesCaptor.capture())).thenReturn("token");

        AuthResponse response = authService.register(new RegisterRequest("multi@batalla.com", "force123"));

        assertThat(rolesCaptor.getValue()).containsExactly("ADMIN", "MODERATOR", "USER");
        assertThat(response.user().roles()).containsExactly("ADMIN", "MODERATOR", "USER");
    }
}
