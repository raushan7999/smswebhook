package com.bestcallerapp.smswebhook.domain

import com.bestcallerapp.smswebhook.domain.engine.PayloadBuilder
import com.bestcallerapp.smswebhook.domain.engine.RegexEvaluator
import com.bestcallerapp.smswebhook.domain.engine.TemplateEngine
import com.bestcallerapp.smswebhook.domain.engine.ValueExtractor
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadField
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFieldKey
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.fakes.FakeDeviceInfo
import com.bestcallerapp.smswebhook.fakes.message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class PayloadBuilderTest {

    private val regex = RegexEvaluator()
    private val builder = PayloadBuilder(ValueExtractor(regex), FakeDeviceInfo)
    private val zone = ZoneOffset.ofHoursMinutes(5, 30)
    private val sms = message()

    @Test fun `default JSON contains sender, message and ISO timestamp`() {
        val built = builder.build(PayloadConfig.default(), sms, zone)
        assertEquals("application/json; charset=utf-8", built.contentType)
        val json = Json.parseToJsonElement(built.body).jsonObject
        assertEquals(setOf("sender", "message", "timestamp"), json.keys)
        assertEquals("VK-HDFCBK", json["sender"]!!.jsonPrimitive.content)
        assertEquals("2026-10-05T20:30:00+05:30", json["timestamp"]!!.jsonPrimitive.content)
    }

    @Test fun `custom field names and optional fields`() {
        val fields = PayloadFieldKey.entries.map { PayloadField(it, enabled = false) }.map {
            when (it.key) {
                PayloadFieldKey.SENDER -> it.copy(outputName = "from", enabled = true)
                PayloadFieldKey.MESSAGE -> it.copy(outputName = "content", enabled = true)
                PayloadFieldKey.RECEIVED_AT -> it.copy(outputName = "receivedAt", enabled = true)
                PayloadFieldKey.SIM_SLOT -> it.copy(enabled = true)
                else -> it
            }
        }
        val json = Json.parseToJsonElement(builder.build(PayloadConfig(fields = fields), sms, zone).body).jsonObject
        assertEquals(setOf("from", "content", "receivedAt", "sim_slot"), json.keys)
        assertEquals("one-based SIM slot", 1, json["sim_slot"]!!.jsonPrimitive.int)
    }

    @Test fun `raw string uses template`() {
        val built = builder.build(PayloadConfig(format = PayloadFormat.RAW_TEXT, template = "{{sender}}: {{message}}"), message(body = "Your OTP is 123456"), zone)
        assertEquals("VK-HDFCBK: Your OTP is 123456", built.body)
        assertEquals("text/plain; charset=utf-8", built.contentType)
    }

    @Test fun `form data is url encoded`() {
        val built = builder.build(PayloadConfig(format = PayloadFormat.FORM_URLENCODED), message(body = "a&b=c ₹"), zone)
        assertEquals("sender=VK-HDFCBK&message=a%26b%3Dc+%E2%82%B9&timestamp=2026-10-05T20%3A30%3A00%2B05%3A30", built.body)
    }

    @Test fun `regex extraction adds named values with capture groups`() {
        val config = PayloadConfig(
            extractors = listOf(
                RegexExtractor("amount", "credited with Rs ([0-9,]+)", 1),
                RegexExtractor("account", "account (X+\\d{4})", 1),
                RegexExtractor("upi", "UPI Ref (\\d+)", 1),
            ),
        )
        val json = Json.parseToJsonElement(builder.build(config, sms, zone).body).jsonObject
        assertEquals("5,000", json["amount"]!!.jsonPrimitive.content)
        assertEquals("XXXX1234", json["account"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, json["upi"])
    }

    @Test fun `JSON template escapes values so output stays valid`() {
        val body = "He said \"hi\"\nnew line \\ backslash"
        val config = PayloadConfig(
            format = PayloadFormat.JSON_TEMPLATE,
            template = """{"text":"{{message}}","amount":"{{regex.amount}}","unknown":"{{nope}}"}""",
            extractors = listOf(RegexExtractor("amount", "Rs ([0-9,]+)")),
        )
        val json = Json.parseToJsonElement(builder.build(config, message(body = body), zone).body).jsonObject
        assertEquals(body, json["text"]!!.jsonPrimitive.content)
        assertEquals("", json["amount"]!!.jsonPrimitive.content)
        assertEquals("", json["unknown"]!!.jsonPrimitive.content)
    }

    @Test fun `template engine is pure substitution`() {
        assertEquals("a=1 b=", TemplateEngine.render("a={{ x }} b={{y}}", mapOf("x" to "1")))
        assertEquals("{{x}}", TemplateEngine.render("{{x}}", mapOf("x" to "{{x}}")))
        assertEquals(setOf("sender", "regex.amount"), TemplateEngine.referencedVariables("{{sender}} {{regex.amount}}"))
    }

    @Test fun `missing capture group yields null`() {
        assertNull(ValueExtractor(regex).extractOne(RegexExtractor("x", "credited", 2), sms.body))
    }
}
