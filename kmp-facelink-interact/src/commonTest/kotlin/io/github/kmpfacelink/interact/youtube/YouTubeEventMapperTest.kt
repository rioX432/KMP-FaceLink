package io.github.kmpfacelink.interact.youtube

import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeEventMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val timestamp = 1700000000000L

    @Test
    fun mapTextMessageEvent() {
        val item = parseJson(TEXT_MESSAGE_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is ChatMessage)
        assertEquals(StreamPlatform.YOUTUBE, event.platform)
        assertEquals("TestUser", event.author)
        assertEquals("Hello from YouTube chat!", event.text)
        assertEquals(timestamp, event.timestampMs)
    }

    @Test
    fun mapTextMessageWithoutDetails() {
        val item = parseJson(TEXT_MESSAGE_NO_DETAILS)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is ChatMessage)
        assertEquals("Fallback display message", event.text)
    }

    @Test
    fun mapSuperChatEvent() {
        val item = parseJson(SUPER_CHAT_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is SuperChat)
        assertEquals(StreamPlatform.YOUTUBE, event.platform)
        assertEquals("SuperChatter", event.author)
        assertEquals(5.0, event.amount) // 5000000 micros = $5.00
        assertEquals("USD", event.currency)
        assertEquals("Great stream!", event.text)
    }

    @Test
    fun mapSuperChatEventNoComment() {
        val item = parseJson(SUPER_CHAT_NO_COMMENT)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is SuperChat)
        assertEquals("", event.text)
        assertEquals(10.0, event.amount) // 10000000 micros = $10.00
        assertEquals("EUR", event.currency)
    }

    @Test
    fun mapSuperStickerEvent() {
        val item = parseJson(SUPER_STICKER_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals(StreamPlatform.YOUTUBE, event.platform)
        assertEquals("StickerFan", event.author)
        assertEquals(2.0, event.amount) // 2000000 micros = $2.00
        assertEquals("USD", event.currency)
        assertEquals(1, event.tier)
    }

    @Test
    fun mapSuperStickerWithTier() {
        val item = parseJson(SUPER_STICKER_WITH_TIER)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals(3, event.tier)
        assertEquals("JPY", event.currency)
    }

    @Test
    fun mapMembershipGiftingEvent() {
        val item = parseJson(MEMBERSHIP_GIFTING_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals(StreamPlatform.YOUTUBE, event.platform)
        assertEquals("GenerousGifter", event.author)
        assertEquals(5.0, event.amount) // 5 gift memberships
        assertEquals("MEMBERSHIP", event.currency)
        assertEquals("Level 1", event.message)
    }

    @Test
    fun mapNewSponsorEvent() {
        val item = parseJson(NEW_SPONSOR_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is Follow)
        assertEquals(StreamPlatform.YOUTUBE, event.platform)
        assertEquals("NewMember", event.author)
        assertEquals(timestamp, event.timestampMs)
    }

    @Test
    fun mapNewSponsorUpgrade() {
        val item = parseJson(NEW_SPONSOR_UPGRADE_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is Follow)
        assertEquals("UpgradedMember", event.author)
    }

    @Test
    fun mapUnknownTypeReturnsNull() {
        val item = parseJson(UNKNOWN_TYPE_ITEM)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNull(event)
    }

    @Test
    fun mapMissingSnippetReturnsNull() {
        val item = parseJson("""{"id": "msg1"}""")
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNull(event)
    }

    @Test
    fun mapMissingAuthorDetailsUsesEmptyName() {
        val item = parseJson(NO_AUTHOR_DETAILS)
        val event = YouTubeEventMapper.map(item, timestamp)
        assertNotNull(event)
        assertTrue(event is ChatMessage)
        assertEquals("", event.author)
    }

    private fun parseJson(raw: String): JsonObject =
        json.parseToJsonElement(raw) as JsonObject

    companion object {
        private val TEXT_MESSAGE_ITEM = """
            {
                "snippet": {
                    "type": "textMessageEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC123",
                    "publishedAt": "2023-11-14T12:00:00Z",
                    "displayMessage": "Hello from YouTube chat!",
                    "textMessageDetails": {
                        "messageText": "Hello from YouTube chat!"
                    }
                },
                "authorDetails": {
                    "channelId": "UC123",
                    "displayName": "TestUser",
                    "profileImageUrl": "https://example.com/photo.jpg",
                    "isChatOwner": false,
                    "isChatModerator": false,
                    "isChatSponsor": false
                }
            }
        """.trimIndent()

        private val TEXT_MESSAGE_NO_DETAILS = """
            {
                "snippet": {
                    "type": "textMessageEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC456",
                    "publishedAt": "2023-11-14T12:00:00Z",
                    "displayMessage": "Fallback display message"
                },
                "authorDetails": {
                    "displayName": "Viewer2"
                }
            }
        """.trimIndent()

        private val SUPER_CHAT_ITEM = """
            {
                "snippet": {
                    "type": "superChatEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC789",
                    "publishedAt": "2023-11-14T12:05:00Z",
                    "displayMessage": "${'$'}5.00 - Great stream!",
                    "superChatDetails": {
                        "amountMicros": 5000000,
                        "currency": "USD",
                        "amountDisplayString": "${'$'}5.00",
                        "userComment": "Great stream!",
                        "tier": 2
                    }
                },
                "authorDetails": {
                    "displayName": "SuperChatter"
                }
            }
        """.trimIndent()

        private val SUPER_CHAT_NO_COMMENT = """
            {
                "snippet": {
                    "type": "superChatEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC111",
                    "publishedAt": "2023-11-14T12:10:00Z",
                    "displayMessage": "",
                    "superChatDetails": {
                        "amountMicros": 10000000,
                        "currency": "EUR",
                        "amountDisplayString": "10.00 EUR",
                        "userComment": "",
                        "tier": 4
                    }
                },
                "authorDetails": {
                    "displayName": "SilentDonor"
                }
            }
        """.trimIndent()

        private val SUPER_STICKER_ITEM = """
            {
                "snippet": {
                    "type": "superStickerEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC222",
                    "publishedAt": "2023-11-14T12:15:00Z",
                    "displayMessage": "",
                    "superStickerDetails": {
                        "superStickerMetadata": {
                            "stickerId": "sticker1",
                            "altText": "Heart sticker",
                            "language": "en"
                        },
                        "amountMicros": 2000000,
                        "currency": "USD",
                        "amountDisplayString": "${'$'}2.00",
                        "tier": 1
                    }
                },
                "authorDetails": {
                    "displayName": "StickerFan"
                }
            }
        """.trimIndent()

        private val SUPER_STICKER_WITH_TIER = """
            {
                "snippet": {
                    "type": "superStickerEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC333",
                    "publishedAt": "2023-11-14T12:20:00Z",
                    "displayMessage": "",
                    "superStickerDetails": {
                        "superStickerMetadata": {
                            "stickerId": "sticker2",
                            "altText": "Star sticker",
                            "language": "ja"
                        },
                        "amountMicros": 500000000,
                        "currency": "JPY",
                        "amountDisplayString": "500 JPY",
                        "tier": 3
                    }
                },
                "authorDetails": {
                    "displayName": "JapanFan"
                }
            }
        """.trimIndent()

        private val MEMBERSHIP_GIFTING_ITEM = """
            {
                "snippet": {
                    "type": "membershipGiftingEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC444",
                    "publishedAt": "2023-11-14T12:25:00Z",
                    "displayMessage": "Gifted 5 memberships",
                    "membershipGiftingDetails": {
                        "giftMembershipsCount": 5,
                        "giftMembershipsLevelName": "Level 1"
                    }
                },
                "authorDetails": {
                    "displayName": "GenerousGifter"
                }
            }
        """.trimIndent()

        private val NEW_SPONSOR_ITEM = """
            {
                "snippet": {
                    "type": "newSponsorEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC555",
                    "publishedAt": "2023-11-14T12:30:00Z",
                    "displayMessage": "Welcome new member!",
                    "newSponsorDetails": {
                        "memberLevelName": "Member",
                        "isUpgrade": false
                    }
                },
                "authorDetails": {
                    "displayName": "NewMember"
                }
            }
        """.trimIndent()

        private val NEW_SPONSOR_UPGRADE_ITEM = """
            {
                "snippet": {
                    "type": "newSponsorEvent",
                    "liveChatId": "chat123",
                    "authorChannelId": "UC666",
                    "publishedAt": "2023-11-14T12:35:00Z",
                    "displayMessage": "Upgraded membership!",
                    "newSponsorDetails": {
                        "memberLevelName": "Super Member",
                        "isUpgrade": true
                    }
                },
                "authorDetails": {
                    "displayName": "UpgradedMember"
                }
            }
        """.trimIndent()

        private val UNKNOWN_TYPE_ITEM = """
            {
                "snippet": {
                    "type": "tombstone",
                    "liveChatId": "chat123",
                    "displayMessage": ""
                },
                "authorDetails": {
                    "displayName": "System"
                }
            }
        """.trimIndent()

        private val NO_AUTHOR_DETAILS = """
            {
                "snippet": {
                    "type": "textMessageEvent",
                    "liveChatId": "chat123",
                    "displayMessage": "No author",
                    "textMessageDetails": {
                        "messageText": "No author"
                    }
                }
            }
        """.trimIndent()
    }
}
