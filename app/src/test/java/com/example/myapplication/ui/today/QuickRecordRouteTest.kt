package com.example.myapplication.ui.today

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRecordRouteTest {

    @Test
    fun `quick record action is recognized`() {
        assertTrue(
            QuickRecordRoute.isQuickRecordAction(
                QuickRecordRoute.ACTION_OPEN_QUICK_RECORD
            )
        )
    }

    @Test
    fun `missing or unrelated actions stay on the normal entry path`() {
        assertFalse(QuickRecordRoute.isQuickRecordAction(null))
        assertFalse(QuickRecordRoute.isQuickRecordAction("android.intent.action.MAIN"))
    }
}
