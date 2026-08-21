package com.starsbattle.battles.domain;

import com.starsbattle.characters.domain.Character;
import com.starsbattle.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A PVE or PVP battle. A Battle references up to three distinct {@link User}
 * rows (initiator/opponent/winner) and two {@link Character} rows
 * (initiator/opponent) via lazy {@code @ManyToOne} associations rather than
 * flat FK id columns, so services and DTO projections (e.g. the upcoming
 * battle public view — nested user/character summaries) can fetch-join or
 * navigate the graph directly instead of resolving each id through a
 * separate repository call. Entities are still never serialized directly —
 * responses go through {@code BattleView} DTO projections, so the password
 * hash cannot leak.
 *
 * {@code @Version} adds optimistic locking (a deliberate improvement over the
 * source, which had none) so concurrent turn submissions on the same battle
 * fail fast instead of silently corrupting HP/turn state.
 */
@Entity
@Table(name = "battles")
public class Battle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BattleMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BattleStatus status = BattleStatus.IN_PROGRESS;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiator_user_id", nullable = false)
    private User initiatorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_user_id")
    private User opponentUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_user_id")
    private User winnerUser;

    @Column(name = "winner_is_machine", nullable = false)
    private Boolean winnerIsMachine = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiator_character_id", nullable = false)
    private Character initiatorCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_character_id")
    private Character opponentCharacter;

    @Column(name = "initiator_current_hp", nullable = false)
    private Integer initiatorCurrentHp = 1;

    @Column(name = "opponent_current_hp", nullable = false)
    private Integer opponentCurrentHp = 1;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_turn", nullable = false, length = 10)
    private BattleTurn nextTurn = BattleTurn.INITIATOR;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Battle() {
        // JPA
    }

    public Battle(BattleMode mode, User initiatorUser, Character initiatorCharacter) {
        this.mode = mode;
        this.initiatorUser = initiatorUser;
        this.initiatorCharacter = initiatorCharacter;
    }

    public Long getId() {
        return id;
    }

    public BattleMode getMode() {
        return mode;
    }

    public BattleStatus getStatus() {
        return status;
    }

    public void setStatus(BattleStatus status) {
        this.status = status;
    }

    public User getInitiatorUser() {
        return initiatorUser;
    }

    public User getOpponentUser() {
        return opponentUser;
    }

    public void setOpponentUser(User opponentUser) {
        this.opponentUser = opponentUser;
    }

    public User getWinnerUser() {
        return winnerUser;
    }

    public void setWinnerUser(User winnerUser) {
        this.winnerUser = winnerUser;
    }

    public Boolean getWinnerIsMachine() {
        return winnerIsMachine;
    }

    public void setWinnerIsMachine(Boolean winnerIsMachine) {
        this.winnerIsMachine = winnerIsMachine;
    }

    public Character getInitiatorCharacter() {
        return initiatorCharacter;
    }

    public Character getOpponentCharacter() {
        return opponentCharacter;
    }

    public void setOpponentCharacter(Character opponentCharacter) {
        this.opponentCharacter = opponentCharacter;
    }

    public Integer getInitiatorCurrentHp() {
        return initiatorCurrentHp;
    }

    public void setInitiatorCurrentHp(Integer initiatorCurrentHp) {
        this.initiatorCurrentHp = initiatorCurrentHp;
    }

    public Integer getOpponentCurrentHp() {
        return opponentCurrentHp;
    }

    public void setOpponentCurrentHp(Integer opponentCurrentHp) {
        this.opponentCurrentHp = opponentCurrentHp;
    }

    public Integer getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(Integer turnNumber) {
        this.turnNumber = turnNumber;
    }

    public BattleTurn getNextTurn() {
        return nextTurn;
    }

    public void setNextTurn(BattleTurn nextTurn) {
        this.nextTurn = nextTurn;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
