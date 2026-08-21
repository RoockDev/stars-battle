package com.starsbattle.battles.event;

/**
 * Domain event published (via {@link org.springframework.context.ApplicationEventPublisher})
 * whenever a battle mutation should be broadcast to WebSocket subscribers of
 * {@code /topic/battles/{battleId}} (design's "Data Flow — turn request":
 * WS broadcast fires only {@code AFTER_COMMIT}, so a rolled-back or lock
 * -failed turn never emits a phantom event). Published from three call
 * sites: {@code BattleFinisher} (BATTLE_FINISHED, both PVP and PVE finish
 * paths), {@code PvpBattleService}/{@code PveBattleService} (TURN_APPLIED,
 * the continuing-turn path only — the finish path already gets its event
 * from {@code BattleFinisher}), and {@code BattleCreationService.joinPvp}
 * (BATTLE_JOINED). Consumed exclusively by {@code BattleEventBroadcaster}'s
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record BattleUpdatedEvent(Long battleId, Type type, Object payload) {

    public enum Type {
        TURN_APPLIED,
        BATTLE_FINISHED,
        BATTLE_JOINED
    }
}
