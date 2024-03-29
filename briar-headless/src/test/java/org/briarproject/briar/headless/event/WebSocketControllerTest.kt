package org.briarproject.briar.headless.event

import io.javalin.json.JavalinJson.toJson
import io.javalin.websocket.WsSession
import io.mockk.*
import org.briarproject.bramble.api.identity.AuthorInfo
import org.briarproject.bramble.api.identity.AuthorInfo.Status.VERIFIED
import org.briarproject.bramble.test.ImmediateExecutor
import org.briarproject.bramble.test.TestUtils.getRandomId
import org.briarproject.briar.api.client.SessionId
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.messaging.EVENT_CONVERSATION_MESSAGE
import org.briarproject.briar.headless.messaging.output
import org.eclipse.jetty.websocket.api.WebSocketException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

internal class WebSocketControllerTest : ControllerTest() {

    private val session1 = mockk<WsSession>()
    private val session2 = mockk<WsSession>()

    private val controller = WebSocketControllerImpl(ImmediateExecutor())

    private val header =
        PrivateMessageHeader(
            message.id,
            group.id,
            timestamp,
            true,
            true,
            true,
            true,
            true,
            emptyList()
        )
    private val event = PrivateMessageReceivedEvent(header, contact.id)
    private val outputEvent = OutputEvent(EVENT_CONVERSATION_MESSAGE, event.output(text))

    @Test
    fun testSendEvent() {
        val slot = CapturingSlot<String>()

        every { session1.send(capture(slot)) } just Runs

        controller.sessions.add(session1)
        controller.sendEvent(EVENT_CONVERSATION_MESSAGE, event.output(text))

        assertJsonEquals(slot.captured, outputEvent)
    }

    @Test
    fun testSendEventIOException() {
        testSendEventException(IOException())
    }

    @Test
    fun testSendEventWebSocketException() {
        testSendEventException(WebSocketException())
    }

    private fun testSendEventException(throwable: Throwable) {
        val slot = CapturingSlot<String>()

        every { session1.send(capture(slot)) } throws throwable
        every { session2.send(capture(slot)) } just Runs

        controller.sessions.add(session1)
        controller.sessions.add(session2)
        controller.sendEvent(EVENT_CONVERSATION_MESSAGE, event.output(text))

        verify { session2.send(slot.captured) }
    }

    @Test
    fun testIntroductionRequestEvent() {
        val introductionRequest = IntroductionRequest(
            message.id,
            group.id,
            timestamp,
            true,
            true,
            true,
            true,
            SessionId(getRandomId()),
            author,
            text,
            false,
            AuthorInfo(VERIFIED)
        )
        val introductionRequestEvent =
            IntroductionRequestReceivedEvent(introductionRequest, contact.id)
        val introductionOutputEvent =
            OutputEvent(EVENT_CONVERSATION_MESSAGE, introductionRequestEvent.output())
        val slot = CapturingSlot<String>()

        every { session1.send(capture(slot)) } just Runs

        controller.sessions.add(session1)
        controller.sendEvent(EVENT_CONVERSATION_MESSAGE, introductionRequestEvent.output())
        assertJsonEquals(slot.captured, introductionOutputEvent)
        assertEquals("IntroductionRequest", introductionRequestEvent.output()["type"])
    }

    @Test
    fun testOutputConversationMessageReceivedEvent() {
        val json = """
        {
            "type": "event",
            "name": "ConversationMessageReceivedEvent",
            "data": ${toJson(header.output(contact.id, text))}
        }
        """
        assertJsonEquals(json, outputEvent)
    }

}
