package com.foxugly.pushit_app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {
    @Test fun acceptsAPositiveId() = assertEquals(42, parseDeepLinkNotificationId("42"))

    @Test fun trimsWhitespace() = assertEquals(7, parseDeepLinkNotificationId(" 7 "))

    @Test fun rejectsNull() = assertNull(parseDeepLinkNotificationId(null))

    @Test fun rejectsZeroAndNegative() {
        assertNull(parseDeepLinkNotificationId("0"))
        assertNull(parseDeepLinkNotificationId("-3"))
    }

    @Test fun rejectsNonDigits() {
        assertNull(parseDeepLinkNotificationId("12abc"))
        assertNull(parseDeepLinkNotificationId("+12"))
        assertNull(parseDeepLinkNotificationId("../1"))
    }

    @Test fun rejectsOverlongIds() = assertNull(parseDeepLinkNotificationId("1234567890"))
}
