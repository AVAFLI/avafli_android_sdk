package com.avafli.avaflisdk

/**
 * 3.2.2 — What to do with identity-scoped local state after the server
 * answered about THIS account. Pure; callers apply the plan.
 *
 * Why: the "email submitted" flag, the streak snapshot and the adoption-code
 * stamp are cached locally and were only ever written, never re-checked. When
 * the account they describe is deleted or reset server-side (data erasure, a
 * database reset, a record that went away) the next `registerDevice` mints a
 * FRESH user — and the SDK kept believing the old flags: it skipped email
 * capture and auto-claimed, the backend refused with "email required", and
 * the person sat on a Day-1 dashboard that never advanced (Skape, Oct 2026).
 * Mirrors web 3.1.10 / Flutter 3.2.2.
 *
 * Every rule needs a CURRENT backend; older backends omit these fields and
 * local state is left alone. An opted-out account is also left alone — the
 * 24-hour rejoin clears the whole session itself once the block lifts, and
 * until then local state is deliberately frozen.
 */
internal object IdentityReconcile {

    enum class Plan {
        LEAVE_ALONE,

        /**
         * `isNewUser == true`: the server has no history for this device — drop
         * every per-user key and any queued claim retry. A genuine first install
         * has nothing to drop, so this is a no-op there.
         */
        DROP_ACCOUNT_STATE,

        /**
         * `emailConsentStatus == false` with no parked adoption: no email on file
         * — drop the email flag only (streak history is the server's and is
         * reconciled by the status call as usual). A parked adoption also reports
         * `false`, but that state is routed to the code screen and must keep its
         * stamp.
         */
        DROP_EMAIL_FLAG,
    }

    fun plan(
        isNewUser: Boolean?,
        emailConsentStatus: Boolean?,
        adoptionPending: Boolean?,
        optedOut: Boolean?,
    ): Plan {
        if (optedOut == true) return Plan.LEAVE_ALONE
        if (isNewUser == true) return Plan.DROP_ACCOUNT_STATE
        if (emailConsentStatus == false && adoptionPending != true) return Plan.DROP_EMAIL_FLAG
        return Plan.LEAVE_ALONE
    }
}
