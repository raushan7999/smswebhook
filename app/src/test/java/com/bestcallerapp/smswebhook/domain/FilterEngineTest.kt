package com.bestcallerapp.smswebhook.domain

import com.bestcallerapp.smswebhook.domain.engine.FilterEngine
import com.bestcallerapp.smswebhook.domain.engine.FilterSubject
import com.bestcallerapp.smswebhook.domain.engine.RegexEvaluator
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField.MESSAGE
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField.MESSAGE_LENGTH
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField.RECEIVED_TIME
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField.SENDER
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField.SIM_SLOT
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterNode
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.BETWEEN
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.CONTAINS
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.CONTAINS_ALL_KEYWORDS
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.CONTAINS_ANY_KEYWORD
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.ENDS_WITH
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.EQUALS
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.GREATER_THAN
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.MATCHES_REGEX
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.NOT_CONTAINS
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator.STARTS_WITH
import com.bestcallerapp.smswebhook.fakes.SAMPLE_TIME
import com.bestcallerapp.smswebhook.fakes.and
import com.bestcallerapp.smswebhook.fakes.cond
import com.bestcallerapp.smswebhook.fakes.message
import com.bestcallerapp.smswebhook.fakes.or
import com.bestcallerapp.smswebhook.fakes.webhook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class FilterEngineTest {

    private val engine = FilterEngine(RegexEvaluator())
    private val zone = ZoneOffset.ofHoursMinutes(5, 30)
    private val hdfcCredit = FilterSubject.from(message())
    private val otp = FilterSubject.from(message(sender = "AX-ICICIB", body = "Your OTP is 482913. Do not share it."))

    private fun matches(node: FilterNode, subject: FilterSubject = hdfcCredit) = engine.evaluate(node, subject, zone)

    @Test fun `sender exact, contains, starts and ends with`() {
        assertTrue(matches(cond(SENDER, EQUALS, "vk-hdfcbk")))
        assertFalse(matches(cond(SENDER, EQUALS, "HDFCBK")))
        assertTrue(matches(cond(SENDER, CONTAINS, "HDFC")))
        assertTrue(matches(cond(SENDER, STARTS_WITH, "VK-")))
        assertTrue(matches(cond(SENDER, ENDS_WITH, "BK")))
        assertFalse(matches(cond(SENDER, CONTAINS, "ICICI")))
    }

    @Test fun `message contains and does not contain`() {
        assertTrue(matches(cond(MESSAGE, CONTAINS, "credited")))
        assertFalse(matches(cond(MESSAGE, NOT_CONTAINS, "credited")))
        assertTrue(matches(cond(MESSAGE, NOT_CONTAINS, "debited")))
    }

    @Test fun `case sensitivity is respected`() {
        assertTrue(matches(cond(MESSAGE, CONTAINS, "CREDITED")))
        assertFalse(matches(cond(MESSAGE, CONTAINS, "CREDITED", caseSensitive = true)))
        assertTrue(matches(cond(MESSAGE, CONTAINS, "credited", caseSensitive = true)))
    }

    @Test fun `keyword lists support ANY and ALL`() {
        val keywords = listOf("OTP", "verification", "transaction")
        assertTrue(matches(cond(MESSAGE, CONTAINS_ANY_KEYWORD, keywords = keywords), otp))
        assertFalse(matches(cond(MESSAGE, CONTAINS_ANY_KEYWORD, keywords = keywords)))
        assertTrue(matches(cond(MESSAGE, CONTAINS_ALL_KEYWORDS, keywords = listOf("account", "credited"))))
        assertFalse(matches(cond(MESSAGE, CONTAINS_ALL_KEYWORDS, keywords = listOf("account", "debited"))))
        assertFalse("empty keyword list never matches", matches(cond(MESSAGE, CONTAINS_ANY_KEYWORD, keywords = listOf(" "))))
    }

    @Test fun `regex matching uses find semantics and inline flags`() {
        assertTrue(matches(cond(MESSAGE, MATCHES_REGEX, """\b\d{6}\b"""), otp))
        assertFalse(matches(cond(MESSAGE, MATCHES_REGEX, """\b\d{6}\b""")))
        assertTrue(matches(cond(MESSAGE, MATCHES_REGEX, "(?i)(otp|verification|one.time.password)", caseSensitive = true), otp))
        assertTrue(matches(cond(SENDER, MATCHES_REGEX, "[A-Z]{2}-[A-Z0-9]+", caseSensitive = true)))
        assertTrue(matches(cond(MESSAGE, MATCHES_REGEX, """Rs\s?[0-9,]+""")))
    }

    @Test fun `invalid regex never matches`() {
        assertFalse(matches(cond(MESSAGE, MATCHES_REGEX, "([unclosed")))
    }

    @Test fun `catastrophic regex is aborted instead of hanging`() {
        val evil = FilterSubject.from(message(body = "a".repeat(40) + "!"))
        val start = System.currentTimeMillis()
        assertFalse(engine.evaluate(cond(MESSAGE, MATCHES_REGEX, "^(a+)+$"), evil, zone))
        assertTrue(System.currentTimeMillis() - start < 5_000)
    }

    @Test fun `AND requires all conditions`() {
        assertTrue(matches(and(cond(SENDER, CONTAINS, "HDFC"), cond(MESSAGE, CONTAINS, "credited"))))
        assertFalse(matches(and(cond(SENDER, CONTAINS, "HDFC"), cond(MESSAGE, CONTAINS, "debited"))))
    }

    @Test fun `OR requires any condition`() {
        val bank = or(cond(SENDER, CONTAINS, "HDFC"), cond(SENDER, CONTAINS, "ICICI"))
        assertTrue(matches(bank))
        assertTrue(matches(bank, otp))
        assertFalse(matches(bank, FilterSubject.from(message(sender = "AMAZON"))))
    }

    @Test fun `NOT inverts conditions and groups`() {
        assertFalse(matches(cond(MESSAGE, CONTAINS, "credited", negate = true)))
        val group = and(cond(SENDER, CONTAINS, "HDFC")).copy(negate = true)
        assertFalse(matches(group))
        assertTrue(matches(group, otp))
    }

    @Test fun `nested groups combine`() {
        val tree = and(
            or(cond(SENDER, CONTAINS, "HDFC"), cond(SENDER, CONTAINS, "ICICI")),
            cond(MESSAGE, CONTAINS_ANY_KEYWORD, keywords = listOf("credited", "debited")),
        )
        assertTrue(matches(tree))
        assertFalse(matches(tree, otp))
    }

    @Test fun `disabled nodes are ignored and empty group matches`() {
        assertTrue(matches(FilterGroup()))
        val tree = and(cond(MESSAGE, CONTAINS, "debited").copy(enabled = false), cond(SENDER, CONTAINS, "HDFC"))
        assertTrue(matches(tree))
    }

    @Test fun `numeric fields`() {
        assertTrue(matches(cond(SIM_SLOT, EQUALS, "1")))
        assertFalse(matches(cond(SIM_SLOT, EQUALS, "2")))
        assertFalse("unknown SIM never matches", matches(cond(SIM_SLOT, EQUALS, "1"), FilterSubject.from(message(simSlot = null))))
        assertTrue(matches(cond(MESSAGE_LENGTH, GREATER_THAN, "20")))
        assertTrue(matches(cond(MESSAGE_LENGTH, BETWEEN, "10", secondValue = "500")))
    }

    @Test fun `time of day window including wrap past midnight`() {
        // SAMPLE_TIME is 20:30 at +05:30
        assertTrue(matches(cond(RECEIVED_TIME, BETWEEN, "20:00", secondValue = "21:00")))
        assertTrue(matches(cond(RECEIVED_TIME, BETWEEN, "20:00", secondValue = "06:00")))
        assertFalse(matches(cond(RECEIVED_TIME, BETWEEN, "06:00", secondValue = "20:00")))
        assertTrue(matches(cond(RECEIVED_TIME, GREATER_THAN, "20:30")))
        assertFalse(engine.evaluate(cond(RECEIVED_TIME, BETWEEN, "00:00", secondValue = "12:00"), FilterSubject.from(message(receivedAt = SAMPLE_TIME)), zone))
    }

    @Test fun `common webhook receives everything`() {
        assertTrue(engine.shouldReceive(webhook(), otp, zone))
        assertFalse(engine.shouldReceive(webhook(filter = and(cond(SENDER, CONTAINS, "HDFC"))), otp, zone))
    }

    @Test fun `unicode text matches case-insensitively`() {
        val hindi = FilterSubject.from(message(body = "आपके खाते में ₹500 जमा। ÉTÉ"))
        assertTrue(engine.evaluate(cond(MESSAGE, CONTAINS, "₹500"), hindi, zone))
        assertTrue(engine.evaluate(cond(MESSAGE, CONTAINS, "été"), hindi, zone))
        assertTrue(engine.evaluate(cond(MESSAGE, MATCHES_REGEX, """₹\s?[0-9,]+"""), hindi, zone))
    }
}
