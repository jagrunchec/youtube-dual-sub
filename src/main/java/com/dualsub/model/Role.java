package com.dualsub.model;

public enum Role {
    /** Limited to N videos per week (default 10). */
    LIMITED,
    /** Unlimited access — standard registered user. */
    NORMAL,
    /** Same as NORMAL today; reserved for future extended rights. */
    SUPER,
    /** Full administration access. */
    ADMIN
}
