package com.travelneveralone.tna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Chat files (TNA-90) - same rules as iOS ChatFileTests.
class ChatFileTest {
    private val stored = "https://x.test/storage/v1/object/public/chat-media/abc/123.heic"

    @Test fun photosByName() {
        for (n in listOf("IMG_20261121_1430.HEIC", "photo.jpg", "scan.PNG", "pic.webp")) assertTrue(n, ChatFile.isPhoto(n, null))
    }

    @Test fun otherFilesAreNotPhotos() {
        assertFalse(ChatFile.isPhoto("Билеты.pdf", stored))
        assertFalse(ChatFile.isPhoto("notes", "https://x.test/a/notes"))
    }

    @Test fun withoutANameThePathDecides() {
        assertTrue(ChatFile.isPhoto(null, stored))
        assertTrue(ChatFile.isPhoto("", stored))
        assertTrue(ChatFile.isPhoto(null, "$stored?token=abc"))
    }

    @Test fun localNameKeepsWhatWasSent() {
        assertEquals("Билеты Байкал.pdf", ChatFile.localName("Билеты Байкал.pdf", stored))
        assertEquals("a_b_c.pdf", ChatFile.localName("a/b:c.pdf", stored))
        assertEquals("123.heic", ChatFile.localName(null, stored))
        assertEquals("file.hidden", ChatFile.localName(".hidden", stored))
    }

    @Test fun photoKeepsItsType() {
        assertEquals("image/heic", ChatFile.photoMime("IMG_1.HEIC", null))
        assertEquals("image/png", ChatFile.photoMime(null, "https://x.test/a/b.png"))
        assertEquals("image/jpeg", ChatFile.photoMime("x", null))
    }
}
