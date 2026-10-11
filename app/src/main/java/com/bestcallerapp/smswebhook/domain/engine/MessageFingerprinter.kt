package com.bestcallerapp.smswebhook.domain.engine

import com.bestcallerapp.smswebhook.core.utils.Hashing
import com.bestcallerapp.smswebhook.domain.model.IncomingSms
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable identity for an SMS used for duplicate detection.
 *
 * Inputs: normalised sender, full body, the service-centre timestamp from the PDU and the
 * subscription id. The local receive time is deliberately excluded: when Android re-delivers
 * the same broadcast (e.g. after a process restart) the PDU — and therefore the service-centre
 * timestamp — is identical while the local time differs. Two genuinely different SMS sharing
 * sender, text, SIM *and* service-centre second are treated as one; this trade-off is accepted
 * because forwarding the same text twice within the same second has no practical value.
 */
@Singleton
class MessageFingerprinter @Inject constructor() {

    fun fingerprint(sms: IncomingSms): String {
        val canonical = listOf(
            VERSION,
            normaliseSender(sms.sender),
            sms.body,
            sms.sentAtMillis.toString(),
            (sms.subscriptionId ?: NO_SUBSCRIPTION).toString(),
        ).joinToString(SEPARATOR)
        return Hashing.sha256Hex(canonical)
    }

    private fun normaliseSender(sender: String): String = sender.trim().uppercase()

    private companion object {
        const val VERSION = "v1"
        const val SEPARATOR = "\u001F"
        const val NO_SUBSCRIPTION = -1
    }
}
