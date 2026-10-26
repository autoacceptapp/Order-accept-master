package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenParserTest {

    @Test
    fun testExtractFareVariousFormats() {
        assertEquals(185, ScreenParser.extractFare("₹185.00"))
        assertEquals(250, ScreenParser.extractFare("Total: ₹ 250"))
        assertEquals(75, ScreenParser.extractFare("Rs.75 for this ride"))
        assertEquals(120, ScreenParser.extractFare("Earn 120 INR"))
        assertNull(ScreenParser.extractFare("No fare specified"))
    }

    @Test
    fun testExtractDistanceKilometersAndMeters() {
        assertEquals(4.2f, ScreenParser.extractDistance("Pickup: 4.2 km away") ?: 0f, 0.01f)
        assertEquals(12.5f, ScreenParser.extractDistance("Drop: 12.5 kms") ?: 0f, 0.01f)
        assertEquals(0.8f, ScreenParser.extractDistance("800 m from here") ?: 0f, 0.01f)
        assertNull(ScreenParser.extractDistance("No distance mentioned"))
    }

    @Test
    fun testFindAcceptKeywords() {
        assertEquals("Accept", ScreenParser.findAcceptKeyword("Accept Order"))
        assertEquals("Confirm", ScreenParser.findAcceptKeyword("Confirm Ride"))
        assertEquals("Take Order", ScreenParser.findAcceptKeyword("Take Order"))
        assertEquals("Go", ScreenParser.findAcceptKeyword("GO"))
        assertNull(ScreenParser.findAcceptKeyword("Decline Order"))
    }
}
