package com.adventcalendar.client;

/** Visual state of one door of the calendar. */
public enum DayState {
    /** Future day (or a door without a reward): greyed out with a padlock. */
    LOCKED,
    /** Unlocked and not claimed yet: golden, clickable. */
    AVAILABLE,
    /** Already claimed: green with a check mark. */
    CLAIMED
}
