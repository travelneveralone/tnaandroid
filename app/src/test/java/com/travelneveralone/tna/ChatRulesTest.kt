package com.travelneveralone.tna

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat display rules (iOS ChatAlbumTests, ChatGroupingTests, PollModels, ChatNames):
 * names above bubbles, @-mentions, polls and photo albums.
 */
class ChatRulesTest {
    private fun person(name: String, role: String = "client", admin: Boolean = false, id: UUID = UUID.randomUUID()) =
        ChatParticipant(id, name, "", role, admin, muted = false)

    private fun msg(author: String = "Маша", asCompany: Boolean = false) =
        Message(text = "hi", isMine = false, author = author, time = "10:00", asCompany = asCompany)

    // MARK: - Names above bubbles

    /** TNA-80: {имя} only works in a push; typed into a news item it drops out. */
    @Test fun nameTokenDropsOutOfTheNewsFeed() {
        assertEquals("Привет", NewsText.plain("Привет, {имя}"))
        assertEquals("Скидка 10%!", NewsText.plain("{имя}, скидка 10%!"))
        assertEquals("Едем в горы", NewsText.plain("Едем { имя } в горы"))
        assertEquals("обычный текст ", NewsText.plain("обычный текст "))
    }

    /** TNA-148: the support chat names who answered too («TNA» or the staff name). */
    @Test fun bubblesFromOthersCarryTheAuthorInEveryChat() {
        org.junit.Assert.assertTrue(ChatNames.showsAuthor(msg("Лера"), first = true))
        org.junit.Assert.assertTrue(ChatNames.showsAuthor(msg("TNA", asCompany = true), first = true))
        org.junit.Assert.assertFalse(ChatNames.showsAuthor(msg("Лера"), first = false))
        org.junit.Assert.assertFalse(ChatNames.showsAuthor(msg("Лера").copy(isMine = true), first = true))
    }

    @Test fun partnerIsNeverShownByName() {
        val partner = person("Иван Петров", role = "partner")
        assertEquals(ChatNames.PARTNER, ChatNames.author(msg("Иван Петров"), partner))
        assertEquals(ChatNames.PARTNER, partner.shownName)
        assertEquals("Иван Петров", person("Иван Петров").shownName)
    }

    @Test fun unnamedStaffGetTheirRoleWord() {
        assertEquals("Менеджер", ChatNames.author(msg(""), person("", role = "manager")))
        assertEquals("Гид", ChatNames.author(msg(""), person("", role = "guide")))
        assertEquals("Гид", ChatNames.author(msg(""), person("", admin = true)))
        assertEquals("Участник", ChatNames.author(msg(""), person("")))
        assertEquals("Участник", ChatNames.author(msg(""), null))
        assertEquals("Паша", ChatNames.author(msg("Паша"), person("Паша", role = "guide")))
    }

    @Test fun companyMessageKeepsItsOwnSignature() {
        val partner = person("Иван", role = "partner")
        assertEquals("TNA", ChatNames.author(msg("TNA", asCompany = true), partner))
        assertEquals("Участник", ChatNames.author(msg("", asCompany = true), person("", role = "manager")))
    }

    @Test fun staffTags() {
        assertEquals("гид", staffTag(true, "client"))
        assertEquals("гид", staffTag(false, "guide"))
        assertEquals("гид", staffTag(false, "coordinator"))
        assertEquals("куратор", staffTag(false, "curator"))
        assertEquals("менеджер", staffTag(false, "manager"))
        assertEquals("менеджер", staffTag(false, "admin"))
        assertNull(staffTag(false, "client"))
        assertNull(staffTag(false, "partner"))
        assertEquals("гид", MiniProfile(UUID.randomUUID(), "Паша", isChatAdmin = true).roleTag)
    }

    // MARK: - @-mentions

    @Test fun mentionQueryOnlyForATrailingAtToken() {
        assertEquals("", ChatNames.mentionQuery("@"))
        assertEquals("Ма", ChatNames.mentionQuery("привет @Ма"))
        assertNull(ChatNames.mentionQuery("mail@example"))
        assertNull(ChatNames.mentionQuery("@Маша привет"))
        assertNull(ChatNames.mentionQuery("без упоминаний"))
    }

    @Test fun mentionMatchesByAnyWordExceptMeAndCapsAtEight() {
        val me = UUID.randomUUID()
        val people = listOf(person("Маша Иванова"), person("Иван Машков"), person("Петя"), person("Маша Я", id = me))
        assertEquals(listOf("Маша Иванова", "Иван Машков"), ChatNames.mentionMatches("ма", people, me).map { it.name })
        assertEquals(listOf("Маша Иванова", "Иван Машков"), ChatNames.mentionMatches("ив", people, me).map { it.name })
        assertEquals(3, ChatNames.mentionMatches("", people, me).size)
        assertTrue(ChatNames.mentionMatches(null, people, me).isEmpty())
        assertEquals(8, ChatNames.mentionMatches("", List(12) { person("П$it") }, null).size)
    }

    @Test fun insertMentionReplacesTheToken() {
        assertEquals("привет @Маша Иванова ", ChatNames.insertMention("привет @ма", "Маша Иванова"))
        assertEquals("без собаки", ChatNames.insertMention("без собаки", "Маша"))
    }

    // MARK: - Polls

    @Test fun pollDraftNeedsAQuestionAndTwoFilledOptions() {
        val a = PollDraftOption(text = "Да")
        val b = PollDraftOption(text = "  ")
        val c = PollDraftOption(text = "Нет")
        assertFalse(PollDraft(question = "Едем?", options = listOf(a, b)).isValid)
        assertFalse(PollDraft(question = " ", options = listOf(a, c)).isValid)
        assertTrue(PollDraft(question = "Едем?", options = listOf(a, b, c)).isValid)
        assertFalse("quiz needs a right answer", PollDraft(question = "Едем?", options = listOf(a, c), quiz = true).isValid)
        assertTrue(PollDraft(question = "Едем?", options = listOf(a, c), quiz = true, correct = setOf(c.id)).isValid)
    }

    @Test fun pollBuildNumbersFilledOptionsAndTrims() {
        val a = PollDraftOption(text = " Да ")
        val empty = PollDraftOption(text = "")
        val c = PollDraftOption(text = "Нет")
        val built = PollDraft(question = " Едем? ", descr = "  ", options = listOf(a, empty, c), showNames = false).build()
        assertEquals("Едем?", built.question)
        assertNull(built.description)
        assertEquals(listOf(PollOption("o1", "Да"), PollOption("o2", "Нет")), built.options)
        assertTrue(built.anonymous)
        assertNull(built.closesAt)
        assertFalse(built.closed)
    }

    @Test fun quizMapsTheRightAnswerToItsNewId() {
        val a = PollDraftOption(text = "Да")
        val empty = PollDraftOption(text = "")
        val c = PollDraftOption(text = "Нет")
        val built = PollDraft(question = "?", options = listOf(a, empty, c), quiz = true, correct = setOf(c.id),
            multiple = true, addOptions = true, revote = true).build()
        assertEquals(listOf("o2"), built.correct)
        assertFalse(built.multiple)
        assertFalse(built.addOptions)
        assertFalse(built.revote)
    }

    @Test fun pollDeadlineOnlyWhenAsked() {
        val at = Instant.parse("2027-01-01T10:00:00Z")
        val opts = listOf(PollDraftOption(text = "a"), PollDraftOption(text = "b"))
        assertEquals("2027-01-01T10:00:00Z", PollDraft(question = "?", options = opts, hasDeadline = true, closesAt = at).build().closesAt)
        assertTrue(PollDraft().canAddOption)
        assertFalse(PollDraft(options = List(PollDraft.MAX_OPTIONS) { PollDraftOption() }).canAddOption)
    }

    @Test fun pollStateTallies() {
        val s = PollState(4, false, listOf("o1"), listOf(PollState.Opt("o1", 3, listOf("Маша")), PollState.Opt("o2", 1, emptyList())))
        assertEquals(3, s.count("o1"))
        assertEquals(0, s.count("nope"))
        assertEquals(listOf("Маша"), s.voters("o1"))
        assertTrue(s.voters("nope").isEmpty())
        assertEquals(0.75, s.fraction("o1").toDouble(), 1e-6)
        assertEquals(0.0, PollState(0, false, emptyList(), emptyList()).fraction("o1").toDouble(), 1e-6)
    }

    // MARK: - Photo albums

    private fun photo(album: UUID?) = Message(text = "", isMine = true, author = "Я", time = "", kind = MessageKind.Image, albumId = album)

    @Test fun albumsGroupOnlyNeighbouringPhotosOfOneAlbum() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val rows = listOf(photo(a), photo(a), photo(a), msg(), photo(a), photo(b), photo(b), photo(null)).albumGrouped()
        assertEquals(listOf(3, 1, 1, 2, 1), rows.map { it.size })
    }

    @Test fun aLoneAlbumPhotoStaysASingleBubble() {
        val a = UUID.randomUUID()
        assertEquals(listOf(1, 1), listOf(photo(a), msg()).albumGrouped().map { it.size })
        assertTrue(emptyList<Message>().albumGrouped().isEmpty())
    }
}
