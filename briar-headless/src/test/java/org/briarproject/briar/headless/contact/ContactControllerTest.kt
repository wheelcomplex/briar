package org.briarproject.briar.headless.contact

import io.javalin.BadRequestResponse
import io.javalin.NotFoundResponse
import io.javalin.json.JavalinJson.toJson
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.briarproject.bramble.api.Pair
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.contact.PendingContactId
import org.briarproject.bramble.api.contact.PendingContactState.FAILED
import org.briarproject.bramble.api.contact.PendingContactState.WAITING_FOR_CONNECTION
import org.briarproject.bramble.api.contact.event.ContactAddedEvent
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.db.NoSuchPendingContactException
import org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH
import org.briarproject.bramble.identity.output
import org.briarproject.bramble.test.TestUtils.getPendingContact
import org.briarproject.bramble.test.TestUtils.getRandomBytes
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.json.JsonDict
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class ContactControllerTest : ControllerTest() {

    private val pendingContact = getPendingContact()

    private val controller =
        ContactControllerImpl(contactManager, objectMapper, webSocketController)

    @Test
    fun testEmptyContactList() {
        every { contactManager.contacts } returns emptyList<Contact>()
        every { ctx.json(emptyList<Any>()) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testList() {
        every { contactManager.contacts } returns listOf(contact)
        every { ctx.json(listOf(contact.output())) } returns ctx
        controller.list(ctx)
    }

    @Test
    fun testLink() {
        val link = "briar://link"
        every { contactManager.handshakeLink } returns link
        every { ctx.json(JsonDict("link" to link)) } returns ctx
        controller.getLink(ctx)
    }

    @Test
    fun testAddPendingContact() {
        val link = "briar://briar://adnsyffpsenoc3yzlhr24aegfq2pwan7kkselocill2choov6sbhs"
        val alias = "Alias123"
        val body = """{
            "link": "$link",
            "alias": "$alias"
        }"""
        every { ctx.body() } returns body
        every { contactManager.addPendingContact(link, alias) } returns pendingContact
        every { ctx.json(pendingContact.output()) } returns ctx
        controller.addPendingContact(ctx)
    }

    @Test
    fun testAddPendingContactInvalidLink() {
        val link = "briar://link123"
        val alias = "Alias123"
        val body = """{
            "link": "$link",
            "alias": "$alias"
        }"""
        every { ctx.body() } returns body
        assertThrows(BadRequestResponse::class.java) {
            controller.addPendingContact(ctx)
        }
    }

    @Test
    fun testAddPendingContactMissingLink() {
        val alias = "Alias123"
        val body = """{
            "alias": "$alias"
        }"""
        every { ctx.body() } returns body
        assertThrows(BadRequestResponse::class.java) {
            controller.addPendingContact(ctx)
        }
    }

    @Test
    fun testAddPendingContactInvalidAlias() {
        val link = "briar://briar://adnsyffpsenoc3yzlhr24aegfq2pwan7kkselocill2choov6sbhs"
        val alias = getRandomString(MAX_AUTHOR_NAME_LENGTH + 1)
        val body = """{
            "link": "$link",
            "alias": "$alias"
        }"""
        every { ctx.body() } returns body
        assertThrows(BadRequestResponse::class.java) {
            controller.addPendingContact(ctx)
        }
    }

    @Test
    fun testAddPendingContactMissingAlias() {
        val link = "briar://adnsyffpsenoc3yzlhr24aegfq2pwan7kkselocill2choov6sbhs"
        val body = """{
            "link": "$link"
        }"""
        every { ctx.body() } returns body
        assertThrows(BadRequestResponse::class.java) {
            controller.addPendingContact(ctx)
        }
    }

    @Test
    fun testListPendingContacts() {
        every { contactManager.pendingContacts } returns listOf(
            Pair(pendingContact, WAITING_FOR_CONNECTION)
        )
        val dict = JsonDict(
            "pendingContact" to pendingContact.output(),
            "state" to WAITING_FOR_CONNECTION.output()
        )
        every { ctx.json(listOf(dict)) } returns ctx
        controller.listPendingContacts(ctx)
    }

    @Test
    fun testRemovePendingContact() {
        val id = pendingContact.id
        every { ctx.body() } returns """{"pendingContactId": ${toJson(id.bytes)}}"""
        every { contactManager.removePendingContact(id) } just Runs
        controller.removePendingContact(ctx)
    }

    @Test
    fun testRemovePendingContactInvalidId() {
        every { ctx.body() } returns """{"pendingContactId": "foo"}"""
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testRemovePendingContactTooShortId() {
        val bytes = getRandomBytes(PendingContactId.LENGTH - 1)
        every { ctx.body() } returns """{"pendingContactId": ${toJson(bytes)}}"""
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testRemovePendingContactTooLongId() {
        val bytes = getRandomBytes(PendingContactId.LENGTH + 1)
        every { ctx.body() } returns """{"pendingContactId": ${toJson(bytes)}}"""
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testRemovePendingContactNonexistentId() {
        val id = pendingContact.id
        every { ctx.body() } returns """{"pendingContactId": ${toJson(id.bytes)}}"""
        every { contactManager.removePendingContact(id) } throws NoSuchPendingContactException()
        assertThrows(NotFoundResponse::class.java) {
            controller.removePendingContact(ctx)
        }
    }

    @Test
    fun testDelete() {
        every { ctx.pathParam("contactId") } returns "1"
        every { contactManager.removeContact(ContactId(1)) } just Runs
        controller.delete(ctx)
    }

    @Test
    fun testDeleteInvalidContactId() {
        every { ctx.pathParam("contactId") } returns "foo"
        assertThrows(NotFoundResponse::class.java) {
            controller.delete(ctx)
        }
    }

    @Test
    fun testDeleteNonexistentContactId() {
        every { ctx.pathParam("contactId") } returns "1"
        every { contactManager.removeContact(ContactId(1)) } throws NoSuchContactException()
        assertThrows(NotFoundResponse::class.java) {
            controller.delete(ctx)
        }
    }

    @Test
    fun testContactAddedEvent() {
        val event = ContactAddedEvent(contact.id, contact.isVerified)

        every {
            webSocketController.sendEvent(
                EVENT_CONTACT_ADDED,
                event.output()
            )
        } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testPendingContactStateChangedEvent() {
        val event = PendingContactStateChangedEvent(pendingContact.id, FAILED)

        every {
            webSocketController.sendEvent(
                EVENT_PENDING_CONTACT_STATE_CHANGED,
                event.output()
            )
        } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testPendingContactAddedEvent() {
        val event = PendingContactAddedEvent(pendingContact)

        every {
            webSocketController.sendEvent(
                EVENT_PENDING_CONTACT_ADDED,
                event.output()
            )
        } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testPendingContactRemovedEvent() {
        val event = PendingContactRemovedEvent(pendingContact.id)

        every {
            webSocketController.sendEvent(
                EVENT_PENDING_CONTACT_REMOVED,
                event.output()
            )
        } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testOutputContact() {
        assertNotNull(contact.handshakePublicKey)
        val json = """
            {
                "contactId": ${contact.id.int},
                "author": ${toJson(author.output())},
                "alias" : "${contact.alias}",
                "handshakePublicKey": ${toJson(contact.handshakePublicKey!!.encoded)},
                "verified": ${contact.isVerified}
            }
        """
        assertJsonEquals(json, contact.output())
    }

    @Test
    fun testOutputAuthor() {
        val json = """
            {
                "formatVersion": 1,
                "id": ${toJson(author.id.bytes)},
                "name": "${author.name}",
                "publicKey": ${toJson(author.publicKey.encoded)}
            }
        """
        assertJsonEquals(json, author.output())
    }

    @Test
    fun testOutputContactAddedEvent() {
        val event = ContactAddedEvent(contact.id, contact.isVerified)
        val json = """
            {
                "contactId": ${contact.id.int},
                "verified": ${contact.isVerified}
            }
        """
        assertJsonEquals(json, event.output())
    }

    @Test
    fun testOutputPendingContact() {
        val json = """
            {
                "pendingContactId": ${toJson(pendingContact.id.bytes)},
                "alias": "${pendingContact.alias}",
                "timestamp": ${pendingContact.timestamp}
            }
        """
        assertJsonEquals(json, pendingContact.output())
    }

    @Test
    fun testOutputPendingContactAddedEvent() {
        val event = PendingContactAddedEvent(pendingContact)
        val json = """
            {
                "pendingContact": {
                    "pendingContactId": ${toJson(pendingContact.id.bytes)},
                    "alias": "${pendingContact.alias}",
                    "timestamp": ${pendingContact.timestamp}
                }
            }
        """
        assertJsonEquals(json, event.output())
    }

    @Test
    fun testOutputPendingContactStateChangedEvent() {
        val event = PendingContactStateChangedEvent(pendingContact.id, FAILED)
        val json = """
            {
                "pendingContactId": ${toJson(pendingContact.id.bytes)},
                "state": "failed"
            }
        """
        assertJsonEquals(json, event.output())
    }

    @Test
    fun testOutputPendingContactRemovedEvent() {
        val event = PendingContactRemovedEvent(pendingContact.id)
        val json = """
            {
                "pendingContactId": ${toJson(pendingContact.id.bytes)}
            }
        """
        assertJsonEquals(json, event.output())
    }

}
