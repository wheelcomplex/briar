package org.briarproject.briar.android.attachment;


import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.FileTooBigException;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.observeForeverOnce;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_IMAGE_SIZE;

@NotNullByDefault
class AttachmentCreatorImpl implements AttachmentCreator {

	private static Logger LOG =
			getLogger(AttachmentCreatorImpl.class.getName());

	private final Application app;
	@IoExecutor
	private final Executor ioExecutor;
	private final MessagingManager messagingManager;
	private final AttachmentRetriever retriever;
	private final ImageSizeCalculator imageSizeCalculator;

	private final CopyOnWriteArrayList<Uri> uris = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<AttachmentItemResult> itemResults =
			new CopyOnWriteArrayList<>();

	@Nullable
	private AttachmentCreationTask task;

	@Nullable
	private volatile MutableLiveData<AttachmentResult> result;

	@Inject
	AttachmentCreatorImpl(Application app, @IoExecutor Executor ioExecutor,
			MessagingManager messagingManager, AttachmentRetriever retriever,
			ImageSizeCalculator imageSizeCalculator) {
		this.app = app;
		this.ioExecutor = ioExecutor;
		this.messagingManager = messagingManager;
		this.retriever = retriever;
		this.imageSizeCalculator = imageSizeCalculator;
	}

	@Override
	@UiThread
	public LiveData<AttachmentResult> storeAttachments(
			LiveData<GroupId> groupId, Collection<Uri> newUris) {
		if (task != null || result != null || !uris.isEmpty())
			throw new IllegalStateException();
		MutableLiveData<AttachmentResult> result = new MutableLiveData<>();
		this.result = result;
		uris.addAll(newUris);
		observeForeverOnce(groupId, id -> {
			if (id == null) throw new IllegalStateException();
			boolean needsSize = uris.size() == 1;
			task = new AttachmentCreationTask(messagingManager,
					app.getContentResolver(), this, imageSizeCalculator, id,
					uris, needsSize);
			ioExecutor.execute(() -> task.storeAttachments());
		});
		return result;
	}

	@Override
	@UiThread
	public LiveData<AttachmentResult> getLiveAttachments() {
		MutableLiveData<AttachmentResult> result = this.result;
		if (task == null || result == null || uris.isEmpty())
			throw new IllegalStateException();
		// A task is already running. It will update the result LiveData.
		// So nothing more to do here.
		return result;
	}

	@Override
	@IoExecutor
	public void onAttachmentHeaderReceived(Uri uri, AttachmentHeader h,
			boolean needsSize) {
		// get and cache AttachmentItem for ImagePreview
		try {
			Attachment a = retriever.getMessageAttachment(h);
			AttachmentItem item = retriever.getAttachmentItem(a, needsSize);
			if (item.hasError()) throw new IOException();
			AttachmentItemResult itemResult =
					new AttachmentItemResult(uri, item);
			itemResults.add(itemResult);
			MutableLiveData<AttachmentResult> result = this.result;
			if (result != null) result.postValue(getResult(false));
		} catch (IOException | DbException e) {
			logException(LOG, WARNING, e);
			onAttachmentError(uri, e);
		}
	}

	@Override
	@IoExecutor
	public void onAttachmentError(Uri uri, Throwable t) {
		// get error message
		String errorMsg;
		if (t instanceof UnsupportedMimeTypeException) {
			String mimeType = ((UnsupportedMimeTypeException) t).getMimeType();
			errorMsg = app.getString(
					R.string.image_attach_error_invalid_mime_type, mimeType);
		} else if (t instanceof FileTooBigException) {
			int mb = MAX_IMAGE_SIZE / 1024 / 1024;
			errorMsg = app.getString(R.string.image_attach_error_too_big, mb);
		} else {
			errorMsg = null; // generic error
		}
		AttachmentItemResult itemResult =
				new AttachmentItemResult(uri, errorMsg);
		itemResults.add(itemResult);
		MutableLiveData<AttachmentResult> result = this.result;
		if (result != null) result.postValue(getResult(false));
		// expect to receive a cancel from the UI
	}

	@Override
	@IoExecutor
	public void onAttachmentCreationFinished() {
		MutableLiveData<AttachmentResult> result = this.result;
		if (result != null) result.postValue(getResult(true));
	}

	@Override
	@UiThread
	public List<AttachmentHeader> getAttachmentHeadersForSending() {
		List<AttachmentHeader> headers = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			// check if we are trying to send attachment items with errors
			if (itemResult.getItem() == null) throw new IllegalStateException();
			headers.add(itemResult.getItem().getHeader());
		}
		return headers;
	}

	@Override
	@UiThread
	public void onAttachmentsSent(MessageId id) {
		List<AttachmentItem> items = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			// check if we are trying to send attachment items with errors
			if (itemResult.getItem() == null) throw new IllegalStateException();
			items.add(itemResult.getItem());
		}
		retriever.cachePut(id, items);
		resetState();
	}

	@Override
	@UiThread
	public void cancel() {
		if (task == null) throw new AssertionError();
		task.cancel();
		deleteUnsentAttachments();
		resetState();
	}

	@UiThread
	private void resetState() {
		task = null;
		uris.clear();
		itemResults.clear();
		MutableLiveData<AttachmentResult> result = this.result;
		if (result != null) {
			result.setValue(null);
			this.result = null;
		}
	}

	@Override
	@UiThread
	public void deleteUnsentAttachments() {
		// Make a copy for the IoExecutor as we clear the itemResults soon
		List<AttachmentHeader> headers = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			// check if we are trying to send attachment items with errors
			if (itemResult.getItem() != null)
				headers.add(itemResult.getItem().getHeader());
		}
		ioExecutor.execute(() -> {
			for (AttachmentHeader header : headers) {
				try {
					messagingManager.removeAttachment(header);
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
			}
		});
	}

	private AttachmentResult getResult(boolean finished) {
		// Make a copy of the list,
		// because our copy will continue to change in the background.
		// (As it's a CopyOnWriteArrayList,
		//  the code that receives the result can safely do simple things
		//  like iterating over the list,
		//  but anything that involves calling more than one list method
		//  is still unsafe.)
		Collection<AttachmentItemResult> items = new ArrayList<>(itemResults);
		return new AttachmentResult(items, finished);
	}

}
