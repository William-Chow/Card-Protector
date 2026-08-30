package com.kotlin.card.ui

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * The consent gate in front of the ads.
 *
 * The app shipped with an AdMob banner, an interstitial, the `AD_ID` permission
 * and no consent flow at all. Under the GDPR and Google's own EU user consent
 * policy an EEA or UK user has to be asked before a personalised ad request is
 * made, and the ask has to happen *before* `MobileAds.initialize`. There was
 * nothing here to ask with.
 *
 * **What this changes for users outside the EEA: nothing.** Their consent status
 * resolves to `NOT_REQUIRED`, [canRequestAds] is true on the first check, and
 * ads initialise on the same code path they always did. Inside the EEA, ads now
 * wait for an answer — and if no privacy message has been published in the AdMob
 * console yet, there is no form to show and [canRequestAds] stays false, so no
 * ad is served. That is the correct outcome rather than a bug: serving one
 * without consent is the thing the policy forbids. Publishing the message in
 * AdMob is what turns those requests back on.
 *
 * Deliberately not a Compose or lifecycle-aware type. The consent SDK is
 * Activity-scoped and callback-based, and wrapping it in state holders would add
 * a layer without removing one.
 */
class AdConsent(private val activity: Activity) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    /**
     * Whether an ad request may be made right now.
     *
     * Read live rather than cached: the SDK updates it as the form is dismissed,
     * and a stale copy is the difference between showing an ad and not.
     */
    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    /**
     * Whether the app must offer a way back into the consent form.
     *
     * Not optional where it is true — a user who consented has to be able to
     * change their mind, and an app that cannot let them is out of policy even
     * though its first-run flow was correct.
     */
    val privacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Resolve consent, calling [onResolved] whenever the picture is settled
     * enough to act on.
     *
     * **[onResolved] can fire more than once and the caller must tolerate it.**
     * A returning user who already answered is served immediately from the
     * cached status, without waiting for a network round trip; the same callback
     * then fires again when the update — and any form — completes. Collapsing
     * that into a single call would mean either a blank ad slot for the whole
     * round trip or dropping the second answer, and the second answer is the one
     * that follows the user's actual choice.
     */
    fun gather(onResolved: () -> Unit) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { onResolved() }
            },
            {
                // The update failed — no network, SDK trouble. A choice stored in
                // a previous session survives that, so this asks again rather
                // than assuming the answer was no.
                onResolved()
            }
        )
        if (consentInformation.canRequestAds()) onResolved()
    }

    /** Reopen the form so a user can change the answer they gave. */
    fun showPrivacyOptions(onDismissed: (FormError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> onDismissed(error) }
    }
}
