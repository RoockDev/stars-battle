package com.starsbattle.auth.dto;

import com.starsbattle.users.domain.Role;
import com.starsbattle.users.domain.User;

import java.util.List;

/** Never includes the password hash — entities are never serialized directly. */
public record UserSummary(
        Long id, String email, Integer level, Integer xp, Integer wins, Integer losses, List<String> roles) {

    public static UserSummary from(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
        return from(user, roleNames);
    }

    /**
     * Builds the summary from an already-computed role list instead of
     * re-deriving it from {@code user.getRoles()}. Lets callers that also
     * need the role names elsewhere (e.g. the JWT 'roles' claim) compute
     * them once and reuse the exact same, consistently-ordered list here —
     * avoiding two independent derivations from the same backing
     * {@code HashSet} silently drifting out of order with each other.
     */
    public static UserSummary from(User user, List<String> roles) {
        return new UserSummary(
                user.getId(), user.getEmail(), user.getLevel(), user.getXp(), user.getWins(), user.getLosses(),
                roles);
    }
}
