package com.travelneveralone.tna

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Packing checklist (0173). Same cases as iOS TripChecklistTests. */
class TripChecklistTest {
    private class MapSets : StringSetStore {
        val map = mutableMapOf<String, Set<String>>()
        override fun get(key: String) = map[key].orEmpty()
        override fun set(key: String, value: Set<String>) { map[key] = value }
        override fun clear() = map.clear()
    }

    private val list = listOf("Паспорт", "Термобельё", "Пауэрбанк")

    @Test fun ticksSurviveANewChecklistOverTheSameStore() {
        val store = MapSets()
        TripChecklist(store).setDone("d1", setOf("Паспорт", "Пауэрбанк"))
        assertEquals(setOf("Паспорт", "Пауэрбанк"), TripChecklist(store).done("d1", list))
    }

    @Test fun ticksOfOneTripDoNotLeakIntoAnother() {
        val c = TripChecklist(MapSets())
        c.setDone("d1", setOf("Паспорт"))
        assertTrue(c.done("d2", list).isEmpty())
    }

    @Test fun itemsRemovedFromTheListDropOut() {
        val c = TripChecklist(MapSets())
        c.setDone("d1", setOf("Паспорт", "Старый пункт"))
        assertEquals(setOf("Паспорт"), c.done("d1", list))
    }

    @Test fun signOutForgetsTheTicks() {
        val c = TripChecklist(MapSets())
        c.setDone("d1", setOf("Паспорт"))
        c.clear()
        assertTrue(c.done("d1", list).isEmpty())
    }

    @Test fun catalogTourReadsThePackingListAndToleratesAnOldCache() {
        val base = """"id":"11111111-0000-0000-0000-000000000001","slug":"baikal","name":"Байкал","tagline":"",
            "hashtag":"","region":"Россия","days":5,"km":0,"price_from":1,"difficulty":1,"max_altitude":0,
            "symbol":"snowflake","color_start":"000000","color_end":"FFFFFF","image_url":null,"video_url":null,
            "includes":[],"tour_days":[],"departures":[],"tour_images":[]"""
        val withList = Mappers.tour(Json.parseToJsonElement("{$base,\"packing_list\":[\"Паспорт\",\"Термобельё\"]}").jsonObject)
        assertEquals(listOf("Паспорт", "Термобельё"), withList.packingList)
        val old = Mappers.tour(Json.parseToJsonElement("{$base}").jsonObject)
        assertTrue(old.packingList.isEmpty())
    }
}
