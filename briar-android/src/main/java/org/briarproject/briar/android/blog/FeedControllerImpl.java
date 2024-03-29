package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchGroupException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.event.GroupAddedEvent;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.event.BlogPostAddedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.api.blog.BlogManager.CLIENT_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class FeedControllerImpl extends BaseControllerImpl implements FeedController {

	private static final Logger LOG =
			Logger.getLogger(FeedControllerImpl.class.getName());

	// UI thread
	private FeedListener listener;

	@Inject
	FeedControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManager notificationManager,
			IdentityManager identityManager, BlogManager blogManager) {
		super(dbExecutor, lifecycleManager, eventBus, notificationManager,
				identityManager, blogManager);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (listener == null) throw new IllegalStateException();
		notificationManager.blockAllBlogPostNotifications();
		notificationManager.clearAllBlogPostNotifications();
	}

	@Override
	public void onStop() {
		super.onStop();
		notificationManager.unblockAllBlogPostNotifications();
	}

	@Override
	public void setFeedListener(FeedListener listener) {
		super.setBlogListener(listener);
		this.listener = listener;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof BlogPostAddedEvent) {
			BlogPostAddedEvent b = (BlogPostAddedEvent) e;
			LOG.info("Blog post added");
			listener.onBlogPostAdded(b.getHeader(), b.isLocal());
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			if (g.getGroup().getClientId().equals(CLIENT_ID)) {
				LOG.info("Blog added");
				listener.onBlogAdded();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getClientId().equals(CLIENT_ID)) {
				LOG.info("Blog removed");
				listener.onBlogRemoved();
			}
		}
	}

	@Override
	public void loadBlogPosts(
			ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler) {
		runOnDbThread(() -> {
			try {
				long start = now();
				Collection<BlogPostItem> posts = new ArrayList<>();
				for (Blog b : blogManager.getBlogs()) {
					try {
						posts.addAll(loadItems(b.getId()));
					} catch (NoSuchGroupException | NoSuchMessageException e) {
						logException(LOG, WARNING, e);
					}
				}
				logDuration(LOG, "Loading all posts", start);
				handler.onResult(posts);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void loadPersonalBlog(
			ResultExceptionHandler<Blog, DbException> handler) {
		runOnDbThread(() -> {
			try {
				long start = now();
				Author a = identityManager.getLocalAuthor();
				Blog b = blogManager.getPersonalBlog(a);
				logDuration(LOG, "Loading personal blog", start);
				handler.onResult(b);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

}
