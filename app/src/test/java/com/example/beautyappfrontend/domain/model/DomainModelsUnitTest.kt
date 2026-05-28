package com.example.beautyappfrontend.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsUnitTest {

    @Test
    fun normalizeScheduleWeeks_null_fills_four_week_grid() {
        val out = normalizeScheduleWeeks(null)
        assertEquals(MasterScheduleData.WEEK_COUNT, out.size)
        assertEquals(MasterScheduleData.DAY_COUNT, out[0].size)
        assertEquals(emptyList<Int>(), out[0][0])
    }

    @Test
    fun normalizeScheduleWeeks_filters_and_sorts_hours() {
        val raw = List(MasterScheduleData.WEEK_COUNT) { wi ->
            List(MasterScheduleData.DAY_COUNT) { di ->
                if (wi == 0 && di == 0) listOf(4, 20, 18, 10, 10) else emptyList()
            }
        }
        val day0 = normalizeScheduleWeeks(raw)[0][0]
        assertEquals(listOf(10, 18), day0)
    }

    @Test
    fun masterScheduleData_dimensions() {
        assertEquals(4, MasterScheduleData.WEEK_COUNT)
        assertEquals(7, MasterScheduleData.DAY_COUNT)
        assertEquals(8, MasterScheduleData.HOUR_START)
        assertEquals(19, MasterScheduleData.HOUR_END_INCLUSIVE)
    }

    @Test
    fun modUser_displayName_prefers_full_name() {
        val u = ModUser(id = 1, email = "z@z.com", firstName = "Zoe", lastName = "Sun")
        assertEquals("Zoe Sun", u.displayName)
    }

    @Test
    fun modUser_displayName_falls_back_to_email() {
        val u = ModUser(id = 2, email = "only@example.com", firstName = "", lastName = "")
        assertEquals("only@example.com", u.displayName)
    }

    @Test
    fun modUser_roleLabel_priority_staff_over_master() {
        val staff = ModUser(isStaff = true, isMaster = true)
        assertEquals("Staff", staff.roleLabel)
        val master = ModUser(isStaff = false, isMaster = true)
        assertEquals("Master", master.roleLabel)
        val client = ModUser(isStaff = false, isMaster = false)
        assertEquals("Client", client.roleLabel)
    }

    @Test
    fun conversationParticipant_displayName_order() {
        val fromServer =
            ConversationParticipant(
                id = 1,
                displayNameFromServer = "Server Name",
                firstName = "First",
                lastName = "Last",
                username = "login",
            )
        assertEquals("Server Name", fromServer.displayName)

        val combined =
            ConversationParticipant(
                id = 2,
                firstName = "A",
                lastName = "B",
                username = "ignored",
            )
        assertEquals("A B", combined.displayName)

        val userOnly =
            ConversationParticipant(id = 3, username = "solo")
        assertEquals("solo", userOnly.displayName)

        val fallback =
            ConversationParticipant(id = 42, username = "")
        assertEquals("User 42", fallback.displayName)
    }

    @Test
    fun conversation_from_maps_fields() {
        val resp =
            ConversationResponse(
                id = 7,
                participant =
                    ConversationParticipant(
                        id = 9,
                        firstName = "T",
                        lastName = "R",
                        isOnline = true,
                        isStaff = true,
                    ),
                lastMessage = "Hi",
                unreadCount = 3,
            )
        val ui = Conversation.from(resp)
        assertEquals(7, ui.id)
        assertEquals(9, ui.participantId)
        assertTrue(ui.lastMessage.contains("Hi"))
        assertEquals(3, ui.unreadCount)
        assertTrue(ui.isOnline)
        assertTrue(ui.participantIsStaff)
    }

    @Test
    fun conversation_from_null_participant_unknown() {
        val ui =
            Conversation.from(
                ConversationResponse(
                    id = 1,
                    participant = null,
                ),
            )
        assertEquals(0, ui.participantId)
        assertEquals("Unknown", ui.participantName)
    }

    @Test
    fun chatMessage_helpers() {
        val textOnly =
            ChatMessage(
                id = 1,
                conversationId = 1,
                senderId = 2,
                text = "",
                timestamp = "",
                isFromMe = false,
                messageType = "text",
                mediaUrl = "",
            )
        assertFalse(textOnly.hasMedia)

        val withMedia =
            textOnly.copy(messageType = "image", mediaUrl = "https://cdn/x.jpg")
        assertTrue(withMedia.hasMedia)

        assertTrue(withMedia.copy(messageType = "video").isVideo)
        assertFalse(withMedia.copy(messageType = "image").isVideo)
    }

    @Test
    fun chatMessage_from_parses_time_and_defaults_type() {
        val msg =
            ChatMessage.from(
                MessageResponse(
                    id = 5,
                    conversation = 2,
                    senderId = 8,
                    text = "Yo",
                    createdAt = "2026-03-02T09:41:22Z",
                    messageType = "",
                    isFromMe = true,
                ),
            )
        assertEquals("09:41", msg.timestamp)
        assertEquals("text", msg.messageType)
        assertEquals(5, msg.id)
    }

    @Test
    fun profileReportRequest_holder() {
        val req = ProfileReportRequest(reason = "spam", text = "note")
        assertEquals("spam", req.reason)
        assertEquals("note", req.text)
    }

    @Test
    fun unreadTotal_defaults() {
        assertEquals(0, UnreadTotalResponse().unreadTotal)
        assertEquals(7, UnreadTotalResponse(unreadTotal = 7).unreadTotal)
    }
}
