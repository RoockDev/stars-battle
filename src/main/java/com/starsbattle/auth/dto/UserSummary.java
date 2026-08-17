package com.starsbattle.auth.dto;

import com.starsbattle.users.domain.Role;
import com.starsbattle.users.domain.User;

import java.util.List;

/** Never includes the password hash — entities are never serialized directly. */
public record UserSummary(
        Long id, String email, Integer level, Integer xp, Integer wins, Integer losses, List<String> roles) {

    public static UserSummary from(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
        return new UserSummary(
                user.getId(), user.getEmail(), user.getLevel(), user.getXp(), user.getWins(), user.getLosses(),
                roleNames);
    }
}
