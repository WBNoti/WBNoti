package com.wbnoti

val CALL_PACKAGES = setOf(
    "com.android.dialer",
    "com.google.android.dialer",
    "com.samsung.android.dialer"
)

val SMS_PACKAGES = setOf(
    "com.google.android.apps.messaging",
    "com.android.mms",
    "com.samsung.android.messaging"
)

val WHATSAPP_PACKAGES = setOf("com.whatsapp")

val SLACK_PACKAGES = setOf("com.Slack")

val CALENDAR_PACKAGES = setOf(
    "com.google.android.calendar",
    "com.samsung.android.calendar",
    "com.android.calendar"
)

// Combined set used for poll-loop and seenKeys tracking
val ALL_MESSAGING_PACKAGES = SMS_PACKAGES + WHATSAPP_PACKAGES + SLACK_PACKAGES
