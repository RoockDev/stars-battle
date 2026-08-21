package com.starsbattle.websocket;

import com.starsbattle.battles.event.BattleUpdatedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * Broadcasts a committed {@link BattleUpdatedEvent} to
 * {@code /topic/battles/{battleId}} (design D8/"Data Flow — turn request").
 * {@code phase = AFTER_COMMIT} is deliberate: an event published inside a
 * transaction that later rolls back (e.g. an {@code OptimisticLockingFailureException}
 * on a concurrent turn) must never reach a subscriber — this listener simply
 * never runs in that case, no explicit rollback-detection needed.
 */
@Component
public class BattleEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public BattleEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onBattleUpdated(BattleUpdatedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/battles/" + event.battleId(),
                new BattleBroadcastMessage(event.type(), event.payload()));
    }

    /** Wire shape sent to subscribers — mirrors the source's {@code {type, data}} ws payload. */
    public record BattleBroadcastMessage(BattleUpdatedEvent.Type type, Object data) {
    }
}
