package com.adventcalendar.data;

/** Outcome of a claim attempt; every failure carries its own message key. */
public enum ClaimResult {
    SUCCESS,
    SUCCESS_STOCKED,
    ALREADY_CLAIMED,
    LOCKED,
    NO_REWARD,
    INVALID_DAY,
    DENIED;

    public boolean isSuccess() {
        return this == SUCCESS || this == SUCCESS_STOCKED;
    }

    /** Translation key describing the result, used for the chat/action bar feedback. */
    public String messageKey() {
        switch (this) {
            case ALREADY_CLAIMED:
                return "adventcalendar.msg.already_claimed";
            case LOCKED:
                return "adventcalendar.msg.locked";
            case NO_REWARD:
                return "adventcalendar.msg.no_reward";
            case INVALID_DAY:
                return "adventcalendar.msg.invalid_day";
            case DENIED:
                return "adventcalendar.msg.op_required";
            default:
                return "adventcalendar.msg.claimed";
        }
    }
}
