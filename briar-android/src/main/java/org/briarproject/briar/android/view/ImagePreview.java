package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.attachment.AttachmentItemResult;

import java.util.Collection;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.support.v4.content.ContextCompat.getColor;
import static android.support.v7.widget.RecyclerView.NO_POSITION;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static java.util.Objects.requireNonNull;

@NotNullByDefault
public class ImagePreview extends ConstraintLayout {

	private final RecyclerView imageList;

	@Nullable
	private ImagePreviewListener listener;

	public ImagePreview(Context context) {
		this(context, null);
	}

	public ImagePreview(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ImagePreview(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		// inflate layout
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
		inflater.inflate(R.layout.image_preview, this, true);

		// set background color
		setBackgroundColor(getColor(context, R.color.card_background));

		// find list
		imageList = findViewById(R.id.imageList);
		imageList.addItemDecoration(new ImagePreviewDecoration(context));

		// set cancel listener
		findViewById(R.id.imageCancelButton).setOnClickListener(view -> {
			if (listener != null) listener.onCancel();
		});
	}

	void setImagePreviewListener(ImagePreviewListener listener) {
		this.listener = listener;
	}

	void showPreview(Collection<ImagePreviewItem> items) {
		if (listener == null) throw new IllegalStateException();
		if (items.size() == 1) {
			LayoutParams params = (LayoutParams) imageList.getLayoutParams();
			params.width = MATCH_PARENT;
			imageList.setLayoutParams(params);
		}
		setVisibility(VISIBLE);
		ImagePreviewAdapter adapter = new ImagePreviewAdapter(items);
		imageList.setAdapter(adapter);
	}

	void loadPreviewImage(AttachmentItemResult result) {
		ImagePreviewAdapter adapter =
				((ImagePreviewAdapter) imageList.getAdapter());
		int pos = requireNonNull(adapter).loadItemPreview(result);
		if (pos != NO_POSITION) {
			imageList.smoothScrollToPosition(pos);
		}
	}

	interface ImagePreviewListener {
		void onCancel();
	}

}
