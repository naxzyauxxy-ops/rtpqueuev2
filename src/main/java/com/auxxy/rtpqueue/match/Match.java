package com.auxxy.rtpqueue.match;

import org.bukkit.Location;

import java.util.UUID;

/**
 * A single running RTP match between two players.
 * MADE BY AUXXY
 */
public final class Match {

    public enum State {
        PREPARING,
        COUNTDOWN,
        GRACE,
        FIGHTING,
        ENDED
    }

    private final UUID first;
    private final UUID second;
    private final String worldName;
    private final Location firstOrigin;
    private final Location secondOrigin;
    private final long createdAt = System.currentTimeMillis();

    private State state = State.PREPARING;

    /** Where each player was teleported to. Set once the arena is picked. */
    private Location firstSpot;
    private Location secondSpot;

    public Match(UUID first, UUID second, String worldName,
                 Location firstOrigin, Location secondOrigin) {
        this.first = first;
        this.second = second;
        this.worldName = worldName;
        this.firstOrigin = firstOrigin;
        this.secondOrigin = secondOrigin;
    }

    public UUID first() {
        return first;
    }

    public UUID second() {
        return second;
    }

    public String worldName() {
        return worldName;
    }

    public Location origin(UUID id) {
        return first.equals(id) ? firstOrigin : secondOrigin;
    }

    public UUID opponentOf(UUID id) {
        return first.equals(id) ? second : first;
    }

    public boolean contains(UUID id) {
        return first.equals(id) || second.equals(id);
    }

    /** The arena location for a player, or null before the teleport happens. */
    public Location spot(UUID id) {
        return first.equals(id) ? firstSpot : secondSpot;
    }

    public void spots(Location firstSpot, Location secondSpot) {
        this.firstSpot = firstSpot;
        this.secondSpot = secondSpot;
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
    }

    public boolean damageAllowed() {
        return state == State.FIGHTING;
    }

    public long createdAt() {
        return createdAt;
    }
}
