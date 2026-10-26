package com.example

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionExecutorTest {

    @Test
    fun testNullNode_returnsFalse() {
        val executor = ActionExecutor(cooldownMs = 1000L)
        val result = executor.performClick(null)
        assertFalse(result.success)
    }
}
