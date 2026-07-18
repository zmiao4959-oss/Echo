package com.example.myapplication.ui.today

/** Shared contract for opening the app directly at today's quick-record input. */
object QuickRecordRoute {
    const val ACTION_OPEN_QUICK_RECORD =
        "com.example.myapplication.action.OPEN_QUICK_RECORD"
    const val RESULT_FOCUS_QUICK_INPUT =
        "com.example.myapplication.result.FOCUS_QUICK_INPUT"

    fun isQuickRecordAction(action: String?): Boolean =
        action == ACTION_OPEN_QUICK_RECORD
}
