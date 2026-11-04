package com.sujal.sleeptimer.core.designsystem

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DialDragTest {
    @Test fun crossingTwelveAtMaximumNeverWrapsToOne() {
        val drag = DialDrag(60, 359f)
        assertThat(drag.move(4f)).isEqualTo(60)
        assertThat(drag.move(10f)).isEqualTo(60)
        assertThat(drag.move(4f)).isEqualTo(59)
    }

    @Test fun crossingTwelveAtMinimumNeverWrapsToSixty() {
        val drag = DialDrag(1, 6f)
        assertThat(drag.move(359f)).isEqualTo(1)
        assertThat(drag.move(350f)).isEqualTo(1)
        assertThat(drag.move(356f)).isEqualTo(2)
    }

    @Test fun snapsToWholeMinutesAndCustomDurationReturnsToDialRange() {
        val normal = DialDrag(30, 180f)
        assertThat(normal.move(188f)).isEqualTo(31)
        assertThat(normal.move(189f)).isEqualTo(32)
        val custom = DialDrag(120, 0f)
        assertThat(custom.move(354f)).isEqualTo(59)
    }
}
