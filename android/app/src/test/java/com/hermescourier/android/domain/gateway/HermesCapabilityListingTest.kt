package com.hermescourier.android.domain.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesCapabilityListingTest {

    @Test
    fun parseCapabilityListing_bareArray() {
        val listing = parseCapabilityListing(
            """[{"skillId":"s1","name":"Alpha"},{"skillId":"s2","name":"Beta","enabled":false}]""",
        ) { it.toSkill() }

        assertTrue(listing.isSupported)
        assertNull(listing.unavailable)
        assertEquals(2, listing.items.size)
        assertEquals("s1", listing.items[0].skillId)
        assertEquals("Alpha", listing.items[0].name)
        assertFalse(listing.items[1].enabled)
    }

    @Test
    fun parseCapabilityListing_itemsEnvelope() {
        val listing = parseCapabilityListing(
            """{"items":[{"memoryId":"m1","title":"Note","pinned":true}]}""",
        ) { it.toMemoryItem() }
        assertEquals(1, listing.items.size)
        assertTrue(listing.items[0].pinned)
    }

    @Test
    fun parseCapabilityListing_unavailablePayloadMapsCleanly() {
        val listing = parseCapabilityListing(
            """
            {
              "type":"skills_unavailable",
              "detail":"Skills surface not yet implemented by gateway.",
              "endpoint":"/v1/skills",
              "supported":false,
              "fallbackPollEndpoints":["/v1/dashboard"]
            }
            """.trimIndent(),
        ) { it.toSkill() }

        assertFalse(listing.isSupported)
        assertTrue(listing.items.isEmpty())
        assertNotNull(listing.unavailable)
        val payload = listing.unavailable!!
        assertEquals("skills_unavailable", payload.type)
        assertEquals("/v1/skills", payload.endpoint)
        assertEquals(listOf("/v1/dashboard"), payload.fallbackPollEndpoints)
    }

    @Test
    fun parseCapabilityListing_blankBodyIsEmptySupportedList() {
        val listing = parseCapabilityListing("") { it.toLogEntry() }
        assertTrue(listing.isSupported)
        assertTrue(listing.items.isEmpty())
    }

    @Test
    fun parseCapabilityListing_garbageBodyIsEmptySupportedList() {
        val listing = parseCapabilityListing("not-json") { it.toCronJob() }
        assertTrue(listing.isSupported)
        assertTrue(listing.items.isEmpty())
    }

    @Test
    fun parseCapabilityListing_explicitSupportedTrueIsNotTreatedAsUnavailable() {
        val listing = parseCapabilityListing(
            """{"items":[{"cronId":"c1","name":"Nightly","schedule":"0 0 * * *"}],"supported":true}""",
        ) { it.toCronJob() }
        assertTrue(listing.isSupported)
        assertEquals(1, listing.items.size)
        assertEquals("Nightly", listing.items[0].name)
    }
}
