package com.direk.dkfly.fly;

/**
 * Why a player can't fly right now.
 * Each has a message for "you can't turn it on" and one for "it was turned off".
 * Author: direk james
 */
public enum Denial {
    NONE(null, null),
    NO_TIME("no-time", "time-expired"),
    WORLD("denied-world", "disabled-world"),
    REGION("denied-region", "disabled-region"),
    ISLAND("denied-island", "disabled-island"),
    COMBAT("denied-combat", "disabled-combat");

    public final String denyKey;
    public final String disabledKey;

    Denial(String denyKey, String disabledKey) {
        this.denyKey = denyKey;
        this.disabledKey = disabledKey;
    }
}
