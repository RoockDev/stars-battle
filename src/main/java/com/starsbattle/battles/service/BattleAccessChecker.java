package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.common.exception.ForbiddenException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Battle view/room authorization (spec: "GET /battles/:id" — "ADMIN can
 * view any battle; otherwise caller must be initiator or opponent"; also
 * design's WebSocket "Room Join Authorization" needs the identical
 * participant-or-admin shape, so this component is reused there in PR13).
 */
@Component
public class BattleAccessChecker {

    private static final String FORBIDDEN_VIEW_MESSAGE = "No tienes permiso para ver esta batalla";

    public void assertCanView(Battle battle, Long callerUserId, boolean callerIsAdmin) {
        if (callerIsAdmin) {
            return;
        }
        if (!isParticipant(battle, callerUserId)) {
            throw new ForbiddenException(FORBIDDEN_VIEW_MESSAGE);
        }
    }

    public boolean isParticipant(Battle battle, Long userId) {
        boolean isInitiator = Objects.equals(battle.getInitiatorUser().getId(), userId);
        boolean isOpponent = battle.getOpponentUser() != null
                && Objects.equals(battle.getOpponentUser().getId(), userId);
        return isInitiator || isOpponent;
    }
}
