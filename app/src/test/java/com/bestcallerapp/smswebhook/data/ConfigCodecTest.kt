package com.bestcallerapp.smswebhook.data

import com.bestcallerapp.smswebhook.data.serialization.FilterDefinitionCodec
import com.bestcallerapp.smswebhook.data.serialization.JsonConfigCodec
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.domain.port.ConfigFormatException
import com.bestcallerapp.smswebhook.fakes.and
import com.bestcallerapp.smswebhook.fakes.cond
import com.bestcallerapp.smswebhook.fakes.or
import com.bestcallerapp.smswebhook.fakes.webhook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodecTest {

    @Test fun `filter tree round-trips`() {
        val tree = and(
            or(cond(FilterField.SENDER, FilterOperator.CONTAINS, "HDFC"), cond(FilterField.SENDER, FilterOperator.CONTAINS, "ICICI")),
            cond(FilterField.MESSAGE, FilterOperator.CONTAINS_ANY_KEYWORD, keywords = listOf("credited", "debited"), negate = true),
        )
        assertEquals(tree, FilterDefinitionCodec.decode(FilterDefinitionCodec.encode(tree)))
    }

    @Test fun `unknown operators from newer versions are disabled not guessed`() {
        val json = """{"type":"group","id":"g","operator":"AND","children":[{"type":"condition","id":"c","field":"SENDER","operator":"FUZZY","value":"x"}]}"""
        val condition = FilterDefinitionCodec.decode(json).children.single() as FilterCondition
        assertFalse(condition.enabled)
    }

    @Test fun `export omits secrets unless requested`() {
        val codec = JsonConfigCodec()
        val hook = webhook().copy(
            headers = listOf(WebhookHeader("Authorization", "Bearer s3cr3t", true), WebhookHeader("X-Env", "prod", false)),
            payload = PayloadConfig(extractors = listOf(RegexExtractor("amount", "Rs ([0-9,]+)"))),
        )
        val withoutSecrets = codec.encode(listOf(hook), includeSecrets = false, exportedAtMillis = 1)
        assertFalse(withoutSecrets.contains("s3cr3t"))
        val imported = codec.decode(withoutSecrets).single()
        assertTrue(imported.headers[0].needsReentry)
        assertEquals("prod", imported.headers[1].value)
        assertEquals(hook.payload.extractors, imported.payload.extractors)

        val withSecrets = codec.decode(codec.encode(listOf(hook), includeSecrets = true, exportedAtMillis = 1)).single()
        assertEquals("Bearer s3cr3t", withSecrets.headers[0].value)
    }

    @Test(expected = ConfigFormatException::class)
    fun `garbage is rejected`() {
        JsonConfigCodec().decode("not json")
    }
}
