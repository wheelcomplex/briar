package org.briarproject.briar.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.StringRes;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;
import android.widget.TextView;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.sharing.event.ContactLeftShareableEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class SharingStatusActivity extends BriarActivity
		implements EventListener {

	@Inject
	ConnectionRegistry connectionRegistry;
	@Inject
	EventBus eventBus;

	private static final Logger LOG =
			Logger.getLogger(SharingStatusActivity.class.getName());

	private GroupId groupId;
	private BriarRecyclerView list;
	private SharingStatusAdapter adapter;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_sharing_status);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);

		list = findViewById(R.id.list);
		adapter = new SharingStatusAdapter(this);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.nobody));

		TextView info = findViewById(R.id.info);
		info.setText(getInfoText());
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
		loadSharedWith();
	}

	@Override
	public void onStop() {
		super.onStop();
		adapter.clear();
		eventBus.removeListener(this);
		list.showProgressBar();
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof ContactLeftShareableEvent) {
			ContactLeftShareableEvent c = (ContactLeftShareableEvent) e;
			if (c.getGroupId().equals(getGroupId())) {
				loadSharedWith();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getId().equals(getGroupId())) {
				supportFinishAfterTransition();
			}
		}
		// TODO ContactConnectedEvent and ContactDisconnectedEvent
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@StringRes
	abstract int getInfoText();

	@DatabaseExecutor
	abstract protected Collection<Contact> getSharedWith() throws DbException;

	protected GroupId getGroupId() {
		return groupId;
	}

	protected void loadSharedWith() {
		runOnDbThread(() -> {
			try {
				List<ContactItem> contactItems = new ArrayList<>();
				for (Contact c : getSharedWith()) {
					boolean online = connectionRegistry.isConnected(c.getId());
					ContactItem item = new ContactItem(c, online);
					contactItems.add(item);
				}
				displaySharedWith(contactItems);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void displaySharedWith(List<ContactItem> contacts) {
		runOnUiThreadUnlessDestroyed(() -> {
			adapter.clear();
			if (contacts.isEmpty()) list.showData();
			else adapter.addAll(contacts);
		});
	}

}
