package com.starsbattle.websocket;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.battles.service.BattleAccessChecker;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked {@link JwtDecoder}/{@link BattleRepository}/
 * {@link BattleAccessChecker}, a REAL {@link JwtAuthenticationConverter}
 * (cheap, pure, configured identically to {@code JwtBeansConfig} — roles
 * claim to {@code ROLE_*}) — spec: "WebSocket Connection Auth", "Room Join
 * Authorization" (design D8). The real STOMP socket round trip is
 * intentionally NOT exercised here to avoid flakiness; wiring is proven by
 * {@code WebSocketConfig} loading in the full application context (covered
 * indirectly by every {@code @SpringBootTest} IT that boots successfully).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompAuthChannelInterceptorTest {

    private static final Long BATTLE_ID = 42L;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private BattleRepository battleRepository;

    @Mock
    private BattleAccessChecker battleAccessChecker;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        interceptor = new StompAuthChannelInterceptor(jwtDecoder, jwtAuthenticationConverter, battleRepository,
                battleAccessChecker);
    }

    @Test
    void connectWithValidBearerTokenAuthenticatesTheStompSession() {
        when(jwtDecoder.decode("valid-token")).thenReturn(jwtFor("1", "USER"));
        StompHeaderAccessor accessor = connectAccessor("Bearer valid-token");
        Message<byte[]> message = build(accessor);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        StompHeaderAccessor accessor = connectAccessor(null);
        Message<byte[]> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("bad signature"));
        StompHeaderAccessor accessor = connectAccessor("Bearer bad-token");
        Message<byte[]> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeToBattleRoomByParticipantIsAllowed() {
        Battle battle = pveBattle();
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(battleAccessChecker.isParticipant(battle, 1L)).thenReturn(true);
        StompHeaderAccessor accessor = subscribeAccessor("/topic/battles/" + BATTLE_ID, "1", "USER");
        Message<byte[]> message = build(accessor);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    @Test
    void subscribeToBattleRoomByNonParticipantIsRejected() {
        Battle battle = pveBattle();
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(battleAccessChecker.isParticipant(battle, 999L)).thenReturn(false);
        StompHeaderAccessor accessor = subscribeAccessor("/topic/battles/" + BATTLE_ID, "999", "USER");
        Message<byte[]> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeToBattleRoomByAdminIsAllowedWithoutParticipantCheck() {
        Battle battle = pveBattle();
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        StompHeaderAccessor accessor = subscribeAccessor("/topic/battles/" + BATTLE_ID, "999", "ADMIN");
        Message<byte[]> message = build(accessor);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        verify(battleAccessChecker, never()).isParticipant(any(), any());
    }

    @Test
    void subscribeToBattleRoomWithoutPriorConnectAuthIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/battles/" + BATTLE_ID);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeToBattleRoomWhenBattleMissingIsRejected() {
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.empty());
        StompHeaderAccessor accessor = subscribeAccessor("/topic/battles/" + BATTLE_ID, "1", "USER");
        Message<byte[]> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeToNonBattleTopicPassesThroughWithoutAuthorization() {
        StompHeaderAccessor accessor = subscribeAccessor("/topic/other-thing", "1", "USER");
        Message<byte[]> message = build(accessor);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        verify(battleRepository, never()).findById(any());
    }

    private StompHeaderAccessor connectAccessor(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private StompHeaderAccessor subscribeAccessor(String destination, String userId, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(jwtAuthentication(userId, role));
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuthentication(
            String userId, String role) {
        Jwt jwt = jwtFor(userId, role);
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                authoritiesConverter.convert(jwt));
    }

    private Jwt jwtFor(String subject, String role) {
        return Jwt.withTokenValue("token-" + subject)
                .header("alg", "HS256")
                .subject(subject)
                .claim("roles", List.of(role))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Message<byte[]> build(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Battle pveBattle() {
        User initiator = mock(User.class);
        when(initiator.getId()).thenReturn(1L);
        Character character = mock(Character.class);
        Battle battle = new Battle(BattleMode.PVE, initiator, character);
        return battle;
    }
}
