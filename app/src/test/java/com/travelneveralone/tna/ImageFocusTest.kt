package com.travelneveralone.tna

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crop around the focus point (TNA-57). Same cases as iOS ImageFocusTests. */
class ImageFocusTest {
    @Test fun centreMatchesThePlainCentredCrop() {
        assertEquals(0.0 to -75.0, ImageFocus.Center.origin(300.0, 450.0, 300.0, 300.0))
    }

    @Test fun topFocusKeepsTheTopOfAVerticalPhoto() {
        // Compare numerically: 0 * negative overflow is -0.0, which Pair.equals tells apart from 0.0.
        val (x, y) = ImageFocus(0.5, 0.0).origin(300.0, 450.0, 300.0, 300.0)
        assertEquals(0.0, x, 0.0)
        assertEquals(0.0, y, 0.0)
    }

    @Test fun focusPointLandsOnTheSameFractionOfTheFrame() {
        val focus = ImageFocus(0.25, 0.2)
        val (ox, oy) = focus.origin(600.0, 400.0, 290.0, 400.0)
        assertEquals(290.0 * 0.25, ox + 600.0 * 0.25, 0.001)
        assertEquals(400.0 * 0.2, oy + 400.0 * 0.2, 0.001)
        assertTrue(ox <= 0 && ox + 600 >= 290)
    }

    @Test fun missingOrBadValuesFallBackToTheCentreOrClamp() {
        assertEquals(ImageFocus.Center, ImageFocus.of(null, null))
        assertEquals(ImageFocus.Center, ImageFocus.of(Double.NaN, Double.POSITIVE_INFINITY))
        assertEquals(ImageFocus(0.0, 1.0), ImageFocus.of(-0.3, 1.7))
    }

    @Test fun tourKnowsTheFocusOfEveryPhoto() {
        val raw = """
            {"id":"11111111-0000-0000-0000-000000000001","name":"Байкал","region":"Россия",
             "image_url":"https://x/a.jpg","image_focus_x":0.1,"image_focus_y":0.2,
             "tour_images":[{"url":"https://x/a.jpg","sort":0,"focus_x":0.1,"focus_y":0.2},
                            {"url":"https://x/b.jpg","sort":1,"focus_x":0.9,"focus_y":0.3},
                            {"url":"https://x/c.jpg","sort":2}]}
        """.trimIndent()
        val tour = Mappers.tour(Json.parseToJsonElement(raw).jsonObject)
        assertEquals(ImageFocus(0.1, 0.2), tour.imageFocus)
        assertEquals(ImageFocus(0.9, 0.3), tour.focusFor("https://x/b.jpg"))
        // A payload cached before 0169 has no focus: centre.
        assertEquals(ImageFocus.Center, tour.focusFor("https://x/c.jpg"))
        assertEquals(ImageFocus.Center, tour.focusFor("https://x/other.jpg"))
    }
}
