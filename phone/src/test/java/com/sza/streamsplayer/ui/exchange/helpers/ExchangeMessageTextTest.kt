package com.sza.streamsplayer.ui.exchange.helpers

import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import com.sza.streamsplayer.domain.model.ExchangeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeMessageTextTest {

    @Test
    fun `every ExchangeMessage resolves to a non-zero string resource`() {
        for (message in ExchangeMessage.entries) {
            val res = ExchangeMessageText.what(message)
            assertNotEquals("Message $message must resolve to a valid string resource", 0, res)
        }
    }

    @Test
    fun `every ExchangeCheckStep resolves to a non-zero string resource`() {
        for (step in ExchangeCheckStep.entries) {
            val res = ExchangeMessageText.advice(step)
            assertNotEquals("Step $step must resolve to a valid string resource", 0, res)
        }
    }

    @Test
    fun `all 15 ladder steps resolve to distinct string resources`() {
        val stepResSet = ExchangeCheckStep.entries.map { ExchangeMessageText.advice(it) }.toSet()
        assertEquals(ExchangeCheckStep.entries.size, stepResSet.size)
    }

    @Test
    fun `all messages resolve to distinct string resources`() {
        val msgResSet = ExchangeMessage.entries.map { ExchangeMessageText.what(it) }.toSet()
        assertEquals(ExchangeMessage.entries.size, msgResSet.size)
    }
}
