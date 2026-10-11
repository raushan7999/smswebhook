package com.bestcallerapp.smswebhook.domain

import com.bestcallerapp.smswebhook.domain.engine.DeliveryResultClassifier
import com.bestcallerapp.smswebhook.domain.engine.MessageFingerprinter
import com.bestcallerapp.smswebhook.domain.engine.MessageStatusAggregator
import com.bestcallerapp.smswebhook.domain.engine.RetryDecision
import com.bestcallerapp.smswebhook.domain.engine.RetryPolicy
import com.bestcallerapp.smswebhook.domain.model.AttemptOutcome
import com.bestcallerapp.smswebhook.domain.model.DeleteSmsPolicy
import com.bestcallerapp.smswebhook.domain.model.DeliveryErrorType
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus.FAILED
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus.FILTERED
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus.PENDING
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus.PROCESSING
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus.RETRYING
import com.bestcallerapp.smswebhook.domain.model.DeliveryStatus.SENT
import com.bestcallerapp.smswebhook.domain.model.MessageStatus
import com.bestcallerapp.smswebhook.domain.model.filter.FilterCondition
import com.bestcallerapp.smswebhook.domain.model.filter.FilterGroup
import com.bestcallerapp.smswebhook.domain.model.filter.FilterTreeOps
import com.bestcallerapp.smswebhook.domain.port.HttpResult
import com.bestcallerapp.smswebhook.domain.usecase.SmsDeletionCoordinator
import com.bestcallerapp.smswebhook.fakes.sms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFingerprinterTest {
    private val fp = MessageFingerprinter()

    @Test fun `duplicate broadcast of the same PDU yields the same fingerprint`() {
        // A re-delivered broadcast has the same PDU (service-centre time) but a later local receive time.
        assertEquals(fp.fingerprint(sms(receivedAt = 1)), fp.fingerprint(sms(receivedAt = 99_999)))
        assertEquals("sender normalisation", fp.fingerprint(sms(sender = "vk-hdfcbk ")), fp.fingerprint(sms(sender = "VK-HDFCBK")))
    }

    @Test fun `different content, time or SIM yields different fingerprints`() {
        val base = fp.fingerprint(sms())
        assertNotEquals(base, fp.fingerprint(sms(body = "Different")))
        assertNotEquals(base, fp.fingerprint(sms(sentAt = 1L)))
        assertNotEquals(base, fp.fingerprint(sms(subscriptionId = 2)))
        assertNotEquals(base, fp.fingerprint(sms(sender = "AX-ICICIB")))
        assertEquals(64, base.length)
    }
}

class RetryPolicyTest {
    private val policy = RetryPolicy()

    @Test fun `retries until the budget is exhausted`() {
        assertTrue(policy.decide(attemptsMade = 1, maxRetries = 3, baseDelaySeconds = 30) is RetryDecision.Retry)
        assertTrue(policy.decide(attemptsMade = 3, maxRetries = 3, baseDelaySeconds = 30) is RetryDecision.Retry)
        assertEquals(RetryDecision.GiveUp, policy.decide(attemptsMade = 4, maxRetries = 3, baseDelaySeconds = 30))
        assertEquals("zero retries", RetryDecision.GiveUp, policy.decide(attemptsMade = 1, maxRetries = 0, baseDelaySeconds = 30))
    }

    @Test fun `backoff is exponential, floored and capped`() {
        assertEquals(30_000L, policy.backoffMillis(1, 30))
        assertEquals(60_000L, policy.backoffMillis(2, 30))
        assertEquals(120_000L, policy.backoffMillis(3, 30))
        assertEquals("WorkManager minimum", 10_000L, policy.backoffMillis(1, 1))
        assertEquals(RetryPolicy.MAX_BACKOFF_MILLIS, policy.backoffMillis(30, 3_600))
    }
}

class DeliveryResultClassifierTest {
    private val classifier = DeliveryResultClassifier()
    private fun outcome(code: Int) = classifier.classify(HttpResult.Response(code, null, 1)).outcome

    @Test fun `2xx is success`() {
        assertEquals(AttemptOutcome.SUCCESS, outcome(200))
        assertEquals(AttemptOutcome.SUCCESS, outcome(204))
    }

    @Test fun `temporary failures are retryable`() {
        listOf(408, 425, 429, 500, 502, 503).forEach { assertEquals("$it", AttemptOutcome.RETRYABLE_FAILURE, outcome(it)) }
        listOf(DeliveryErrorType.TIMEOUT, DeliveryErrorType.DNS, DeliveryErrorType.CONNECTION).forEach {
            assertEquals(AttemptOutcome.RETRYABLE_FAILURE, classifier.classify(HttpResult.Failure(it, "x", 1)).outcome)
        }
    }

    @Test fun `permanent failures are not retried`() {
        listOf(301, 400, 401, 403, 404, 422).forEach { assertEquals("$it", AttemptOutcome.PERMANENT_FAILURE, outcome(it)) }
        assertEquals(
            AttemptOutcome.PERMANENT_FAILURE,
            classifier.classify(HttpResult.Failure(DeliveryErrorType.SSL, "bad cert", 1)).outcome,
        )
    }
}

class MessageStatusAggregatorTest {
    @Test fun `aggregate reflects the most relevant state`() {
        assertEquals(MessageStatus.FILTERED, MessageStatusAggregator.aggregate(emptyList()))
        assertEquals(MessageStatus.FILTERED, MessageStatusAggregator.aggregate(listOf(FILTERED, FILTERED)))
        assertEquals(MessageStatus.SENT, MessageStatusAggregator.aggregate(listOf(SENT, FILTERED, SENT)))
        assertEquals(MessageStatus.FAILED, MessageStatusAggregator.aggregate(listOf(SENT, FAILED)))
        assertEquals(MessageStatus.PENDING, MessageStatusAggregator.aggregate(listOf(FAILED, PENDING)))
        assertEquals(MessageStatus.RETRYING, MessageStatusAggregator.aggregate(listOf(RETRYING, PENDING)))
        assertEquals(MessageStatus.PROCESSING, MessageStatusAggregator.aggregate(listOf(PROCESSING, RETRYING)))
    }
}

class SmsDeletionPolicyTest {
    @Test fun `policies decide based on non-filtered deliveries`() {
        assertFalse(SmsDeletionCoordinator.shouldDelete(DeleteSmsPolicy.DISABLED, listOf(SENT)))
        assertTrue(SmsDeletionCoordinator.shouldDelete(DeleteSmsPolicy.AFTER_FIRST_SUCCESS, listOf(SENT, PENDING)))
        assertFalse(SmsDeletionCoordinator.shouldDelete(DeleteSmsPolicy.AFTER_ALL_SUCCEEDED, listOf(SENT, PENDING)))
        assertTrue(SmsDeletionCoordinator.shouldDelete(DeleteSmsPolicy.AFTER_ALL_SUCCEEDED, listOf(SENT, FILTERED)))
        assertFalse(SmsDeletionCoordinator.shouldDelete(DeleteSmsPolicy.AFTER_ALL_SUCCEEDED, listOf(FILTERED)))
    }
}

class FilterTreeOpsTest {
    @Test fun `add, update and remove nodes by id`() {
        val inner = FilterGroup()
        var root = FilterGroup(children = listOf(inner))
        val condition = FilterCondition(value = "x")
        root = FilterTreeOps.addChild(root, inner.id, condition)
        assertEquals(condition, (root.children[0] as FilterGroup).children.single())
        root = FilterTreeOps.update(root, condition.id) { (it as FilterCondition).copy(value = "y") }
        assertEquals("y", ((root.children[0] as FilterGroup).children.single() as FilterCondition).value)
        assertEquals(2, FilterTreeOps.depthOf(root, inner.id))
        root = FilterTreeOps.remove(root, condition.id)
        assertTrue((root.children[0] as FilterGroup).children.isEmpty())
    }
}
