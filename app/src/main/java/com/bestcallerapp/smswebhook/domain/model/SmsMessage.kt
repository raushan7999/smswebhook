package com.bestcallerapp.smswebhook.domain.model

/** An SMS as captured from the system broadcast, before it is persisted. */
data class IncomingSms(
    val sender: String,
    val body: String,
    /** Service-centre timestamp carried in the PDU; stable across duplicate broadcasts. */
    val sentAtMillis: Long,
    /** Local wall-clock time at which the broadcast was handled. */
    val receivedAtMillis: Long,
    val subscriptionId: Int?,
    /** Zero-based physical SIM slot, when known. */
    val simSlot: Int?,
    val partCount: Int,
)

data class SmsMessage(
    val id: Long,
    val fingerprint: String,
    val sender: String,
    val body: String,
    val sentAtMillis: Long,
    val receivedAtMillis: Long,
    val subscriptionId: Int?,
    val simSlot: Int?,
    val partCount: Int,
    val status: MessageStatus,
    val createdAtMillis: Long,
)

/** Lightweight projection used by paged lists. */
data class MessageSummary(
    val id: Long,
    val sender: String,
    val preview: String,
    val receivedAtMillis: Long,
    val status: MessageStatus,
)
