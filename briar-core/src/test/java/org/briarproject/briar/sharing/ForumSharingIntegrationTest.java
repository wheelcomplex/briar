package org.briarproject.briar.sharing;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.ConversationResponse;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostHeader;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.forum.event.ForumInvitationRequestReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;

import javax.annotation.Nullable;

import static junit.framework.Assert.assertNotNull;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.forum.ForumSharingManager.CLIENT_ID;
import static org.briarproject.briar.api.forum.ForumSharingManager.MAJOR_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ForumSharingIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private ForumManager forumManager0, forumManager1;
	private MessageEncoder messageEncoder;
	private Listener listener0, listener2, listener1;
	private Forum forum;

	// objects accessed from background threads need to be volatile
	private volatile ForumSharingManager forumSharingManager0;
	private volatile ForumSharingManager forumSharingManager1;
	private volatile ForumSharingManager forumSharingManager2;
	private volatile Waiter eventWaiter;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		forumManager0 = c0.getForumManager();
		forumManager1 = c1.getForumManager();
		forumSharingManager0 = c0.getForumSharingManager();
		forumSharingManager1 = c1.getForumSharingManager();
		forumSharingManager2 = c2.getForumSharingManager();
		messageEncoder = new MessageEncoderImpl(clientHelper, messageFactory);

		// initialize waiter fresh for each test
		eventWaiter = new Waiter();

		listener0 = new Listener();
		c0.getEventBus().addListener(listener0);
		listener1 = new Listener();
		c1.getEventBus().addListener(listener1);
		listener2 = new Listener();
		c2.getEventBus().addListener(listener2);

		addContacts1And2();
		addForumForSharer();
	}

	@Override
	protected void createComponents() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		component.injectBriarEagerSingletons();
		component.inject(this);

		c0 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t0Dir))
				.build();
		c0.injectBriarEagerSingletons();

		c1 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t1Dir))
				.build();
		c1.injectBriarEagerSingletons();

		c2 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t2Dir))
				.build();
		c2.injectBriarEagerSingletons();
	}

	private void addForumForSharer() throws DbException {
		forum = forumManager0.addForum("Test Forum");
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// check that request message state is correct
		Collection<ConversationMessageHeader> messages =
				db0.transactionWithResult(true, txn -> forumSharingManager0
						.getMessageHeaders(txn, contactId1From0));
		assertEquals(1, messages.size());
		assertMessageState(messages.iterator().next(), true, false, false);

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// check that accept message state is correct
		messages = db1.transactionWithResult(true, txn -> forumSharingManager1
				.getMessageHeaders(txn, contactId0From1));
		assertEquals(2, messages.size());
		for (ConversationMessageHeader h : messages) {
			if (h instanceof ConversationResponse) {
				assertMessageState(h, true, false, false);
			}
		}

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());

		// invitee has one invitation message from sharer
		Collection<ConversationMessageHeader> list =
				db1.transactionWithResult(true, txn -> forumSharingManager1
						.getMessageHeaders(txn, contactId0From1));
		assertEquals(2, list.size());
		// check other things are alright with the forum message
		for (ConversationMessageHeader m : list) {
			if (m instanceof ForumInvitationRequest) {
				ForumInvitationRequest invitation = (ForumInvitationRequest) m;
				assertTrue(invitation.wasAnswered());
				assertEquals(forum.getName(), invitation.getName());
				assertEquals(forum, invitation.getNameable());
				assertEquals("Hi!", invitation.getText());
				assertTrue(invitation.canBeOpened());
			} else {
				ForumInvitationResponse response = (ForumInvitationResponse) m;
				assertEquals(forum.getId(), response.getShareableId());
				assertTrue(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2, db0.transactionWithResult(true, txn ->
				forumSharingManager0.getMessageHeaders(txn, contactId1From0))
				.size());
		// forum can not be shared again
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertFalse(forumSharingManager0.canBeShared(forum.getId(), c1));
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertFalse(forumSharingManager1.canBeShared(forum.getId(), c0));
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null,
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee declines
		respondToRequest(contactId0From1, false);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, false);

		// forum was not added
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());
		// forum is no longer available to invitee who declined
		assertEquals(0, forumSharingManager1.getInvitations().size());

		// invitee has one invitation message from sharer and one response
		Collection<ConversationMessageHeader> list =
				db1.transactionWithResult(true, txn -> forumSharingManager1
						.getMessageHeaders(txn, contactId0From1));
		assertEquals(2, list.size());
		// check things are alright with the forum message
		for (ConversationMessageHeader m : list) {
			if (m instanceof ForumInvitationRequest) {
				ForumInvitationRequest invitation = (ForumInvitationRequest) m;
				assertEquals(forum, invitation.getNameable());
				assertTrue(invitation.wasAnswered());
				assertEquals(forum.getName(), invitation.getName());
				assertNull(invitation.getText());
				assertFalse(invitation.canBeOpened());
			} else {
				ForumInvitationResponse response = (ForumInvitationResponse) m;
				assertEquals(forum.getId(), response.getShareableId());
				assertFalse(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2, db0.transactionWithResult(true, txn ->
				forumSharingManager0.getMessageHeaders(txn, contactId1From0))
				.size());
		// forum can be shared again
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.canBeShared(forum.getId(), c1));
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
		assertTrue(forumManager1.getForums().contains(forum));

		// sharer shares forum with invitee
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		// invitee gets forum shared by sharer
		Contact contact0 = contactManager1.getContact(contactId1From0);
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));

		// invitee un-subscribes from forum
		forumManager1.removeForum(forum);

		// send leave message to sharer
		sync1To0(1, true);

		// forum is gone
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());

		// sharer no longer shares forum with invitee
		assertFalse(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		// invitee no longer gets forum shared by sharer
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));
		// forum can be shared again by sharer
		assertTrue(forumSharingManager0
				.canBeShared(forum.getId(), contact1From0));
		// invitee that left can not share again
		assertFalse(forumSharingManager1
				.canBeShared(forum.getId(), contact0From1));
	}

	@Test
	public void testSharerLeavesAfterFinished() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null,
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
		assertTrue(forumManager1.getForums().contains(forum));

		// sharer shares forum with invitee
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(c1));
		// invitee gets forum shared by sharer
		Contact contact0 = contactManager1.getContact(contactId1From0);
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// send leave message to invitee
		sync0To1(1, true);

		// forum is gone for sharer, but not invitee
		assertEquals(0, forumManager0.getForums().size());
		assertEquals(1, forumManager1.getForums().size());

		// invitee no longer shares forum with sharer
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(c0));
		// sharer no longer gets forum shared by invitee
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));
		// forum can be shared again
		assertTrue(forumSharingManager1.canBeShared(forum.getId(), c0));
	}

	@Test
	public void testSharerLeavesBeforeResponse() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null,
						clock.currentTimeMillis());

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// sync request message and leave message
		sync0To1(2, true);

		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// ensure that invitee has no forum invitations available
		assertEquals(0, forumSharingManager1.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());

		// Try again, this time with a response before the leave
		addForumForSharer();

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null,
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// sync leave message
		sync0To1(1, true);

		// ensure that invitee has no forum invitations available
		assertEquals(0, forumSharingManager1.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
	}

	@Test
	public void testSharingSameForumWithEachOther() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// response and invitation got tracked
		Group group = contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, contact0From1);
		assertEquals(2, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum.getId(),
				contactId0From1,
				"I am re-sharing this forum with you.",
				clock.currentTimeMillis());

		// assert that the last invitation wasn't send
		assertEquals(2, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());
	}

	@Test
	public void testSharingSameForumWithEachOtherBeforeAccept()
			throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// ensure that invitee has received the invitations
		assertEquals(1, forumSharingManager1.getInvitations().size());

		// assert that the invitation arrived
		Group group = contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, contact0From1);
		assertEquals(1, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum.getId(),
				contactId0From1,
				"I am re-sharing this forum with you.",
				clock.currentTimeMillis());

		// assert that the last invitation wasn't send
		assertEquals(1, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());
	}

	@Test
	public void testSharingSameForumWithEachOtherAtSameTime() throws Exception {
		// invitee adds the same forum
		db1.transaction(false, txn -> forumManager1.addForum(txn, forum));

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum.getId(),
				contactId0From1, "I am re-sharing this forum with you.",
				clock.currentTimeMillis());

		// only now sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// sync second invitation which counts as accept
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener0, contactId1From0);

		// both peers should share the forum with each other now
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));

		// and both have each other's invitations (and no response)
		assertEquals(2, db0.transactionWithResult(true, txn ->
				forumSharingManager0.getMessageHeaders(txn, contactId1From0))
				.size());
		assertEquals(2, db1.transactionWithResult(true, txn ->
				forumSharingManager1.getMessageHeaders(txn, contactId0From1))
				.size());

		// there are no more open invitations
		assertTrue(forumSharingManager0.getInvitations().isEmpty());
		assertTrue(forumSharingManager1.getInvitations().isEmpty());
	}

	@Test
	public void testContactRemoved() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(1,
				forumSharingManager0.getSharedWith(forum.getId()).size());

		// contacts now remove each other
		removeAllContacts();

		// invitee still has forum
		assertEquals(1, forumManager1.getForums().size());

		// make sure sharer does share the forum with nobody now
		assertEquals(0,
				forumSharingManager0.getSharedWith(forum.getId()).size());

		// contacts add each other again
		addDefaultContacts();
		addContacts1And2();

		// forum can be shared with contacts again
		assertTrue(forumSharingManager0
				.canBeShared(forum.getId(), contact1From0));
		assertTrue(forumSharingManager0
				.canBeShared(forum.getId(), contact2From0));

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum is still there
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(1,
				forumSharingManager0.getSharedWith(forum.getId()).size());
	}

	@Test
	public void testTwoContactsShareSameForum() throws Exception {
		// second sharer adds the same forum
		db2.transaction(false, txn -> db2.addGroup(txn, forum.getGroup()));

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// second sharer sends invitation for same forum
		assertNotNull(contactId1From2);
		forumSharingManager2
				.sendInvitation(forum.getId(), contactId1From2, null,
						clock.currentTimeMillis());

		// sync second request message
		sync2To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId2From1);

		// make sure we now have two invitations to the same forum available
		Collection<SharingInvitationItem> forums =
				forumSharingManager1.getInvitations();
		assertEquals(1, forums.size());
		assertEquals(2, forums.iterator().next().getNewSharers().size());
		assertEquals(forum, forums.iterator().next().getShareable());

		// answer second request
		assertNotNull(contactId2From1);
		Contact contact2From1 = contactManager1.getContact(contactId2From1);
		forumSharingManager1.respondToInvitation(forum, contact2From1, true);

		// sync response
		sync1To2(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener2, contactId1From2, true);

		// answer first request
		Contact contact0From1 = contactManager1.getContact(contactId0From1);
		forumSharingManager1.respondToInvitation(forum, contact0From1, true);

		// sync response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// make sure both sharers actually share the forum
		Collection<Contact> contacts =
				forumSharingManager1.getSharedWith(forum.getId());
		assertEquals(2, contacts.size());
	}

	@Test
	public void testSyncAfterReSharing() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// sharer posts into the forum
		long time = clock.currentTimeMillis();
		String text = getRandomString(42);
		ForumPost p = forumPostFactory
				.createPost(forum.getId(), time, null, author0, text);
		forumManager0.addLocalPost(p);

		// sync forum post
		sync0To1(1, true);

		// make sure forum post arrived
		Collection<ForumPostHeader> headers =
				forumManager1.getPostHeaders(forum.getId());
		assertEquals(1, headers.size());
		ForumPostHeader header = headers.iterator().next();
		assertEquals(p.getMessage().getId(), header.getId());
		assertEquals(author0, header.getAuthor());

		// now invitee creates a post
		time = clock.currentTimeMillis();
		text = getRandomString(42);
		p = forumPostFactory
				.createPost(forum.getId(), time, null, author1, text);
		forumManager1.addLocalPost(p);

		// sync forum post
		sync1To0(1, true);

		// make sure forum post arrived
		headers = forumManager1.getPostHeaders(forum.getId());
		assertEquals(2, headers.size());
		boolean found = false;
		for (ForumPostHeader h : headers) {
			if (p.getMessage().getId().equals(h.getId())) {
				found = true;
				assertEquals(author1, h.getAuthor());
			}
		}
		assertTrue(found);

		// contacts remove each other
		removeAllContacts();

		// contacts add each other back
		addDefaultContacts();
		addContacts1And2();

		// send invitation again
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// now invitee creates a post
		time = clock.currentTimeMillis();
		text = getRandomString(42);
		p = forumPostFactory
				.createPost(forum.getId(), time, null, author1, text);
		forumManager1.addLocalPost(p);

		// sync forum post
		sync1To0(1, true);

		// make sure forum post arrived
		headers = forumManager1.getPostHeaders(forum.getId());
		assertEquals(3, headers.size());
		found = false;
		for (ForumPostHeader h : headers) {
			if (p.getMessage().getId().equals(h.getId())) {
				found = true;
				assertEquals(author1, h.getAuthor());
			}
		}
		assertTrue(found);
	}

	@Test
	public void testSessionResetAfterAbort() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// get invitation MessageId for later
		MessageId invitationId = null;
		Collection<ConversationMessageHeader> list =
				db1.transactionWithResult(true, txn -> forumSharingManager1
						.getMessageHeaders(txn, contactId0From1));
		for (ConversationMessageHeader m : list) {
			if (m instanceof ForumInvitationRequest) {
				invitationId = m.getId();
			}
		}
		assertNotNull(invitationId);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum is shared mutually
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));

		// send an accept message for the same forum
		Message m = messageEncoder.encodeAcceptMessage(
				forumSharingManager0.getContactGroup(contact1From0).getId(),
				forum.getId(), clock.currentTimeMillis(), invitationId);
		c0.getClientHelper().addLocalMessage(m, new BdfDictionary(), true);

		// sync unexpected message and the expected abort message back
		sync0To1(1, true);
		sync1To0(1, true);

		// forum is no longer shared mutually
		assertFalse(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));

		// new invitation is possible now
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null,
						clock.currentTimeMillis());
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// and can be answered
		respondToRequest(contactId0From1, true);
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum is shared mutually again
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));
	}

	private void respondToRequest(ContactId contactId, boolean accept)
			throws DbException {
		assertEquals(1, forumSharingManager1.getInvitations().size());
		SharingInvitationItem invitation =
				forumSharingManager1.getInvitations().iterator().next();
		assertEquals(forum, invitation.getShareable());
		Contact c = contactManager1.getContact(contactId);
		forumSharingManager1.respondToInvitation(forum, c, accept);
	}

	private void assertRequestReceived(Listener listener, ContactId contactId) {
		assertTrue(listener.requestReceived);
		assertEquals(contactId, listener.requestContactId);
		listener.reset();
	}

	private void assertResponseReceived(Listener listener, ContactId contactId,
			boolean accept) {
		assertTrue(listener.responseReceived);
		assertEquals(contactId, listener.responseContactId);
		assertEquals(accept, listener.responseAccepted);
		listener.reset();
	}

	@NotNullByDefault
	private class Listener implements EventListener {

		private volatile boolean requestReceived = false;
		@Nullable
		private volatile ContactId requestContactId = null;

		private volatile boolean responseReceived = false;
		@Nullable
		private volatile ContactId responseContactId = null;
		private volatile boolean responseAccepted = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof ForumInvitationRequestReceivedEvent) {
				ForumInvitationRequestReceivedEvent event =
						(ForumInvitationRequestReceivedEvent) e;
				requestReceived = true;
				requestContactId = event.getContactId();
				eventWaiter.resume();
			} else if (e instanceof ForumInvitationResponseReceivedEvent) {
				ForumInvitationResponseReceivedEvent event =
						(ForumInvitationResponseReceivedEvent) e;
				responseReceived = true;
				responseContactId = event.getContactId();
				responseAccepted = event.getMessageHeader().wasAccepted();
				eventWaiter.resume();
			}
		}

		void reset() {
			requestReceived = responseReceived = responseAccepted = false;
			requestContactId = responseContactId = null;
		}
	}
}
