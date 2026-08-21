package com.starsbattle.websocket;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.battles.service.BattleAccessChecker;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WS double-layer authorization (design D8, spec: "WebSocket Connection
 * Auth" + "Room Join Authorization"). Browsers cannot set an
 * {@code Authorization} header on the WebSocket handshake, so the JWT
 * travels as a STOMP CONNECT header instead — this interceptor validates it
 * there and stores the resulting principal on the STOMP session
 * ({@code accessor.setUser}). On SUBSCRIBE to a {@code /topic/battles/{id}}
 * room, the same principal is re-checked against the DATABASE (not just the
 * JWT's roles claim) via {@link BattleAccessChecker} — the exact
 * participant-or-admin authorization {@code GET /battles/:id} already uses
 * (PR9), reused here rather than reimplemented.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern BATTLE_TOPIC_PATTERN = Pattern.compile("^/topic/battles/(\\d+)$");
    private static final String UNAUTHENTICATED_MESSAGE = "No autenticado";
    private static final String FORBIDDEN_ROOM_MESSAGE = "No tienes permiso para ver esta batalla";
    private static final String BATTLE_NOT_FOUND_MESSAGE = "Batalla no encontrada";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final BattleRepository battleRepository;
    private final BattleAccessChecker battleAccessChecker;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter,
            BattleRepository battleRepository, BattleAccessChecker battleAccessChecker) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.battleRepository = battleRepository;
        this.battleAccessChecker = battleAccessChecker;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // MessageHeaderAccessor.getAccessor (not StompHeaderAccessor.wrap)
        // retrieves the SAME mutable accessor the STOMP decoder attached to
        // this message (setLeaveMutable(true) upstream) — mutating it here
        // (accessor.setUser below) is visible on `message` itself, so no
        // message rebuild is needed before returning it.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor);
        if (token == null) {
            throw new MessagingException(UNAUTHENTICATED_MESSAGE);
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Authentication authentication = jwtAuthenticationConverter.convert(jwt);
            accessor.setUser(authentication);
        } catch (JwtException ex) {
            throw new MessagingException(UNAUTHENTICATED_MESSAGE, ex);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Long battleId = extractBattleId(accessor.getDestination());
        if (battleId == null) {
            // Not a battle-room subscription (e.g. a broker-internal topic) —
            // nothing for this interceptor to authorize.
            return;
        }

        JwtAuthenticationToken principal = requireAuthenticatedPrincipal(accessor);
        Long callerUserId = Long.valueOf(principal.getToken().getSubject());
        boolean isAdmin = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);

        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new MessagingException(BATTLE_NOT_FOUND_MESSAGE));
        if (!isAdmin && !battleAccessChecker.isParticipant(battle, callerUserId)) {
            throw new MessagingException(FORBIDDEN_ROOM_MESSAGE);
        }
    }

    private JwtAuthenticationToken requireAuthenticatedPrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken;
        }
        throw new MessagingException(UNAUTHENTICATED_MESSAGE);
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader("Authorization");
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String header = headers.get(0);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private Long extractBattleId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = BATTLE_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        return Long.valueOf(matcher.group(1));
    }
}
