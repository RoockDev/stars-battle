package com.starsbattle.websocket;

import com.starsbattle.battles.event.BattleUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit layer: mocked {@link SimpMessagingTemplate} — locks down the topic
 * naming convention ({@code /topic/battles/{id}}) and the {@code {type,
 * data}} wire shape (design D8/"Data Flow — turn request"). The
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} wiring itself —
 * that this listener fires only after a real commit, never after a rollback
 * — is proven separately by {@code BattleFinisherAtomicityIT}'s
 * commit/rollback pair, since a plain method call here can't exercise a
 * real transaction boundary.
 */
@ExtendWith(MockitoExtension.class)
class BattleEventBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void broadcastsToTheBattleSpecificTopicWithTypeAndPayload() {
        BattleEventBroadcaster broadcaster = new BattleEventBroadcaster(messagingTemplate);
        Object payload = new Object();
        BattleUpdatedEvent event = new BattleUpdatedEvent(42L, BattleUpdatedEvent.Type.TURN_APPLIED, payload);

        broadcaster.onBattleUpdated(event);

        ArgumentCaptor<Object> sentPayload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/battles/42"), sentPayload.capture());
        assertThat(sentPayload.getValue()).isInstanceOf(BattleEventBroadcaster.BattleBroadcastMessage.class);

        BattleEventBroadcaster.BattleBroadcastMessage message =
                (BattleEventBroadcaster.BattleBroadcastMessage) sentPayload.getValue();
        assertThat(message.type()).isEqualTo(BattleUpdatedEvent.Type.TURN_APPLIED);
        assertThat(message.data()).isSameAs(payload);
    }
}
