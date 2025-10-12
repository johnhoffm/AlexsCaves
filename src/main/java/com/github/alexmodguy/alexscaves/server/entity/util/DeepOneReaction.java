package com.github.alexmodguy.alexscaves.server.entity.util;

import com.github.alexmodguy.alexscaves.server.entity.living.DeepOneBaseEntity;
import net.minecraft.world.entity.player.Player;

public enum DeepOneReaction {
    STALKING(0, 80),
    AGGRESSIVE(0, 50),
    NEUTRAL(30, 40),
    HELPFUL(20, 30);

    // Reputation thresholds. Must be in ascending order.
    public static final int AGGRESSIVE_THRESHOLD = -50;
    public static final int STALKING_THRESHOLD = 50;
    public static final int NEUTRAL_THRESHOLD = 90;

    private double minDistance;
    private double maxDistance;

    DeepOneReaction(double minDistance, double maxDistance) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    public double getMinDistance() {
        return this.minDistance;
    }

    public double getMaxDistance() {
        return this.maxDistance;
    }

    public static DeepOneReaction fromReputation(int rep) {
        if (rep <= AGGRESSIVE_THRESHOLD) {
            return AGGRESSIVE;
        }
        if (rep <= STALKING_THRESHOLD) {
            return STALKING;
        }
        if (rep <= NEUTRAL_THRESHOLD) {
            return NEUTRAL;
        }
        return HELPFUL;
    }

    public boolean validPlayer(DeepOneBaseEntity deepOne, Player player) {
        if (this == STALKING && player.getY() > deepOne.getY() + 15) {
            return false;
        }
        if (this != AGGRESSIVE && this != HELPFUL) {
            return player.isInWaterOrBubble() || !deepOne.isInWaterOrBubble();
        }
        return true;
    }
}
