package org.briarproject.briar.headless

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.BadRequestResponse
import io.javalin.Context
import io.javalin.Javalin
import io.javalin.JavalinEvent.SERVER_START_FAILED
import io.javalin.JavalinEvent.SERVER_STOPPED
import io.javalin.NotFoundResponse
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.core.util.Header.AUTHORIZATION
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.headless.blogs.BlogController
import org.briarproject.briar.headless.contact.ContactController
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.forums.ForumController
import org.briarproject.briar.headless.messaging.MessagingController
import java.lang.Runtime.getRuntime
import java.lang.System.exit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger.getLogger
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class Router
@Inject
constructor(
    private val briarService: BriarService,
    private val webSocketController: WebSocketController,
    private val contactController: ContactController,
    private val messagingController: MessagingController,
    private val forumController: ForumController,
    private val blogController: BlogController
) {

    private val logger = getLogger(Router::javaClass.name)
    private val stopped = AtomicBoolean(false)

    internal fun start(authToken: String, port: Int, debug: Boolean) : Javalin {
        briarService.start()
        getRuntime().addShutdownHook(Thread(this::stop))

        val app = Javalin.create()
            .port(port)
            .disableStartupBanner()
            .enableCaseSensitiveUrls()
            .event(SERVER_START_FAILED) {serverStopped() }
            .event(SERVER_STOPPED) { serverStopped() }
        if (debug) app.enableDebugLogging()

        app.accessManager { handler, ctx, _ ->
            if (ctx.header(AUTHORIZATION) == "Bearer $authToken") {
                handler.handle(ctx)
            } else {
                ctx.status(401).result("Unauthorized")
            }
        }
        app.routes {
            path("/v1") {
                path("/contacts") {
                    get { ctx -> contactController.list(ctx) }
                    path("add") {
                        path("link") {
                            get { ctx -> contactController.getLink(ctx) }
                        }
                        path("pending") {
                            get { ctx -> contactController.listPendingContacts(ctx) }
                            post { ctx -> contactController.addPendingContact(ctx) }
                            delete { ctx -> contactController.removePendingContact(ctx) }
                        }
                    }
                    path("/:contactId") {
                        delete { ctx -> contactController.delete(ctx) }
                    }
                }
                path("/messages/:contactId") {
                    get { ctx -> messagingController.list(ctx) }
                    post { ctx -> messagingController.write(ctx) }
                }
                path("/forums") {
                    get { ctx -> forumController.list(ctx) }
                    post { ctx -> forumController.create(ctx) }
                }
                path("/blogs") {
                    path("/posts") {
                        get { ctx -> blogController.listPosts(ctx) }
                        post { ctx -> blogController.createPost(ctx) }
                    }
                }
            }
        }
        app.ws("/v1/ws") { ws ->
            if (logger.isLoggable(Level.INFO)) ws.onConnect { session ->
                logger.info("Received websocket connection from ${session.remoteAddress}")
                logger.info("Waiting for authentication")
            }
            ws.onMessage { session, msg ->
                if (msg == authToken && !webSocketController.sessions.contains(session)) {
                    logger.info("Authenticated websocket session with ${session.remoteAddress}")
                    webSocketController.sessions.add(session)
                } else {
                    logger.info("Invalid message received: $msg")
                    logger.info("Closing websocket connection with ${session.remoteAddress}")
                    session.close(1008, "Invalid Authentication Token")
                }
            }
            ws.onClose { session, _, _ ->
                logger.info("Removing websocket connection with ${session.remoteAddress}")
                webSocketController.sessions.remove(session)
            }
        }
        return app.start()
    }

    private fun serverStopped() {
        stop()
        exit(1)
    }

    internal fun stop() {
        if (!stopped.getAndSet(true)) {
            briarService.stop()
        }
    }

}

/**
 * Returns a [ContactId] from the "contactId" path parameter.
 *
 * @throws NotFoundResponse when contactId is not a number.
 */
fun Context.getContactIdFromPathParam(): ContactId {
    val contactString = pathParam("contactId")
    val contactInt = try {
        Integer.parseInt(contactString)
    } catch (e: NumberFormatException) {
        throw NotFoundResponse()
    }
    return ContactId(contactInt)
}

/**
 * Returns a String from the JSON field or throws [BadRequestResponse] if null or empty.
 */
fun Context.getFromJson(objectMapper: ObjectMapper, field: String) : String {
    try {
        val jsonNode = objectMapper.readTree(body())
        if (!jsonNode.hasNonNull(field)) throw BadRequestResponse("'$field' missing in JSON")
        val result = jsonNode.get(field).asText()
        if (result == null || result.isEmpty()) throw BadRequestResponse("'$field' empty in JSON")
        return result
    } catch (e: JsonParseException) {
        throw BadRequestResponse("Invalid JSON")
    }
}
