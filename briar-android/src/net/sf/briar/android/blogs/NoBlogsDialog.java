package net.sf.briar.android.blogs;

import net.sf.briar.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class NoBlogsDialog extends DialogFragment {

	private Listener listener = null;

	void setListener(Listener listener) {
		this.listener = listener;
	}

	@Override
	public Dialog onCreateDialog(Bundle state) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.no_blogs);
		builder.setPositiveButton(R.string.create_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.blogCreationSelected();
			}
		});
		builder.setNegativeButton(R.string.cancel_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.blogCreationCancelled();
			}
		});
		return builder.create();
	}

	interface Listener {

		void blogCreationSelected();

		void blogCreationCancelled();
	}
}