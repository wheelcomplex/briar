package org.briarproject.android.forum;

import android.support.annotation.Nullable;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.threaded.ThreadListControllerImpl;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.ForumPostReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

public class ForumControllerImpl
		extends ThreadListControllerImpl<Forum, ForumEntry, ForumPostHeader, ForumPost>
		implements ForumController {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	private final ForumManager forumManager;

	@Inject
	ForumControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			@CryptoExecutor Executor cryptoExecutor,
			ForumManager forumManager, EventBus eventBus,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, cryptoExecutor, eventBus,
				notificationManager);
		this.forumManager = forumManager;
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
		notificationManager.clearForumPostNotification(groupId);
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof ForumPostReceivedEvent) {
			final ForumPostReceivedEvent pe = (ForumPostReceivedEvent) e;
			if (pe.getGroupId().equals(groupId)) {
				LOG.info("Forum post received, adding...");
				final ForumPostHeader fph = pe.getForumPostHeader();
				listener.runOnUiThreadUnlessDestroyed(new Runnable() {
					@Override
					public void run() {
						listener.onHeaderReceived(fph);
					}
				});
			}
		}
	}

	@Override
	protected Forum loadGroupItem() throws DbException {
		return forumManager.getForum(groupId);
	}

	@Override
	protected Collection<ForumPostHeader> loadHeaders() throws DbException {
		return forumManager.getPostHeaders(groupId);
	}

	@Override
	protected void loadBodies(Collection<ForumPostHeader> headers)
			throws DbException {
		for (ForumPostHeader header : headers) {
			if (!bodyCache.containsKey(header.getId())) {
				String body = StringUtils
						.fromUtf8(forumManager.getPostBody(header.getId()));
				bodyCache.put(header.getId(), body);
			}
		}
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		forumManager.setReadFlag(groupId, id, true);
	}

	@Override
	protected ForumPost createLocalMessage(GroupId g, String body,
			@Nullable MessageId parentId) throws DbException {
		return forumManager.createLocalPost(groupId, body, parentId);
	}

	@Override
	protected ForumPostHeader addLocalMessage(ForumPost p)
			throws DbException {
		return forumManager.addLocalPost(p);
	}

	@Override
	protected void deleteGroupItem(Forum forum) throws DbException {
		forumManager.removeForum(forum);
	}

	@Override
	protected ForumEntry buildItem(ForumPostHeader header) {
		return new ForumEntry(header, bodyCache.get(header.getId()));
	}

}
