package com.zuauto.porter.vip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderFareTest {
    @Test
    fun ignoresAddressNumberAndReadsTheActualFare() {
        assertEquals(72, OrderFare.distinctFare(listOf("209", "₹72", "Pickup address"))?.toInt())
        assertNull(OrderFare.distinctFare(listOf("209", "Pickup address", "(2.3 km)")))
    }

    @Test
    fun rejectsConflictingFareLabelsAndAcceptsRepeatedLabels() {
        assertNull(OrderFare.distinctFare(listOf("₹72", "₹209")))
        assertEquals(72, OrderFare.distinctFare(listOf("₹72", "Rs. 72.00"))?.toInt())
    }

    @Test
    fun doesNotRoundUpToTheMinimumFare() {
        assertEquals(199, OrderFare.distinctFare(listOf("₹199.99"))?.toInt())
    }
}
