package org.briarproject.briar.android.account;

import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;

import javax.annotation.Nullable;

import static android.view.inputmethod.EditorInfo.IME_ACTION_NEXT;
import static android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
import static java.util.Objects.requireNonNull;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.toUtf8;
import static org.briarproject.briar.android.util.UiUtils.setError;
import static org.briarproject.briar.android.util.UiUtils.showSoftKeyboard;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AuthorNameFragment extends SetupFragment {

	private final static String TAG = AuthorNameFragment.class.getName();

	private TextInputLayout authorNameWrapper;
	private TextInputEditText authorNameInput;
	private Button nextButton;

	public static AuthorNameFragment newInstance() {
		return new AuthorNameFragment();
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		requireNonNull(getActivity()).setTitle(getString(R.string.setup_title));
		View v = inflater.inflate(R.layout.fragment_setup_author_name,
				container, false);
		authorNameWrapper = v.findViewById(R.id.nickname_entry_wrapper);
		authorNameInput = v.findViewById(R.id.nickname_entry);
		nextButton = v.findViewById(R.id.next);

		authorNameInput.addTextChangedListener(this);
		nextButton.setOnClickListener(this);

		return v;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onResume() {
		super.onResume();
		showSoftKeyboard(authorNameInput);
	}

	@Override
	protected String getHelpText() {
		return getString(R.string.setup_name_explanation);
	}

	@Override
	public void onTextChanged(CharSequence authorName, int i, int i1, int i2) {
		int authorNameLength = toUtf8(authorName.toString().trim()).length;
		boolean error = authorNameLength > MAX_AUTHOR_NAME_LENGTH;
		setError(authorNameWrapper, getString(R.string.name_too_long), error);
		boolean enabled = authorNameLength > 0 && !error;
		authorNameInput
				.setImeOptions(enabled ? IME_ACTION_NEXT : IME_ACTION_NONE);
		authorNameInput.setOnEditorActionListener(enabled ? this : null);
		nextButton.setEnabled(enabled);
	}

	@Override
	public void onClick(View view) {
		Editable text = authorNameInput.getText();
		if (text != null) {
			setupController.setAuthorName(text.toString().trim());
			setupController.showPasswordFragment();
		}
	}

}
