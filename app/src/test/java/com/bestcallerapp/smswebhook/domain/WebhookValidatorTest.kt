package com.bestcallerapp.smswebhook.domain

import com.bestcallerapp.smswebhook.domain.engine.RegexEvaluator
import com.bestcallerapp.smswebhook.domain.engine.WebhookValidator
import com.bestcallerapp.smswebhook.domain.model.ConfigSection
import com.bestcallerapp.smswebhook.domain.model.WebhookHeader
import com.bestcallerapp.smswebhook.domain.model.WebhookScope
import com.bestcallerapp.smswebhook.domain.model.filter.FilterField
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterOperator
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadConfig
import com.bestcallerapp.smswebhook.domain.model.payload.PayloadFormat
import com.bestcallerapp.smswebhook.domain.model.payload.RegexExtractor
import com.bestcallerapp.smswebhook.fakes.and
import com.bestcallerapp.smswebhook.fakes.cond
import com.bestcallerapp.smswebhook.fakes.webhook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookValidatorTest {

    private val validator = WebhookValidator(RegexEvaluator(), allowCleartext = false)

    @Test fun `valid webhook has no issues`() {
        assertEquals(emptyList<Any>(), validator.validate(webhook(filter = and(cond(FilterField.SENDER, FilterOperator.CONTAINS, "HDFC")))))
    }

    @Test fun `urls must be https, absolute and credential-free`() {
        assertNull(validator.validateUrl("https://example.com/sms?x=1"))
        assertNotNull(validator.validateUrl("http://example.com"))
        assertNotNull(validator.validateUrl("example.com/sms"))
        assertNotNull(validator.validateUrl("https://user:pass@example.com"))
        assertNotNull(validator.validateUrl("ftp://example.com"))
        assertNotNull(validator.validateUrl(""))
        assertNull(WebhookValidator(RegexEvaluator(), allowCleartext = true).validateUrl("http://10.0.2.2:8080/hook"))
    }

    @Test fun `invalid regex is rejected with a clear message`() {
        val issues = validator.validateFilters(
            WebhookScope.MATCHING_RULES,
            and(cond(FilterField.MESSAGE, FilterOperator.MATCHES_REGEX, "(unclosed")),
        )
        assertEquals(1, issues.size)
        assertTrue(issues.single().message.startsWith("Invalid regex"))
    }

    @Test fun `filtered scope requires a condition`() {
        assertEquals(1, validator.validateFilters(WebhookScope.MATCHING_RULES, FilterGroup()).size)
        assertTrue(validator.validateFilters(WebhookScope.ALL_MESSAGES, FilterGroup()).isEmpty())
    }

    @Test fun `headers reject injection and reserved names`() {
        val issues = validator.validateHeaders(
            listOf(
                WebhookHeader("X-Ok", "value", false),
                WebhookHeader("Bad Header", "v", false),
                WebhookHeader("X-Inject", "a\r\nEvil: 1", false),
                WebhookHeader("Host", "example.com", false),
                WebhookHeader("X-Name", "Café", false),
            ),
        )
        assertEquals(listOf("header:1", "header:2", "header:3", "header:4"), issues.map { it.target })
    }

    @Test fun `extractor group must exist`() {
        assertNull(validator.validateExtractor(RegexExtractor("amount", "Rs ([0-9,]+)", 1)))
        assertNotNull(validator.validateExtractor(RegexExtractor("amount", "Rs [0-9,]+", 1)))
        assertNotNull(validator.validateExtractor(RegexExtractor("1bad", "x", 0)))
    }

    @Test fun `json template must render valid json`() {
        val good = PayloadConfig(format = PayloadFormat.JSON_TEMPLATE, template = """{"m":"{{message}}"}""")
        val bad = PayloadConfig(format = PayloadFormat.JSON_TEMPLATE, template = """{"m":{{message}}}""")
        val unknown = PayloadConfig(format = PayloadFormat.JSON_TEMPLATE, template = """{"m":"{{nope}}"}""")
        assertTrue(validator.validatePayload(good).isEmpty())
        assertEquals(ConfigSection.PAYLOAD, validator.validatePayload(bad).single().section)
        assertTrue(validator.validatePayload(unknown).single().message.contains("nope"))
    }

    @Test fun `delivery limits`() {
        assertEquals(3, validator.validateDelivery(timeoutSeconds = 0, maxRetries = 99, retryDelaySeconds = 1).size)
    }
}
