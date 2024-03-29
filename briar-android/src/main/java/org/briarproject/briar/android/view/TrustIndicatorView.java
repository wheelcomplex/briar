package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

import org.briarproject.bramble.api.identity.AuthorInfo.Status;
import org.briarproject.briar.R;

@UiThread
public class TrustIndicatorView extends AppCompatImageView {

	public TrustIndicatorView(Context context) {
		super(context);
	}

	public TrustIndicatorView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TrustIndicatorView(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void setTrustLevel(Status status) {
		int res;
		switch (status) {
			case ANONYMOUS:
				res = R.drawable.trust_indicator_anonymous;
				break;
			case UNVERIFIED:
				res = R.drawable.trust_indicator_unverified;
				break;
			case VERIFIED:
				res = R.drawable.trust_indicator_verified;
				break;
			case OURSELVES:
				res = R.drawable.ic_our_identity;
				break;
			default:
				res = R.drawable.trust_indicator_unknown;
		}
		setImageDrawable(ContextCompat.getDrawable(getContext(), res));
		setVisibility(VISIBLE);

		invalidate();
		requestLayout();
	}

}
