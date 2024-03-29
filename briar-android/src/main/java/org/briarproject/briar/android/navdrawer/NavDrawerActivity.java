package org.briarproject.briar.android.navdrawer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.blog.FeedFragment;
import org.briarproject.briar.android.contact.ContactListFragment;
import org.briarproject.briar.android.controller.handler.UiResultHandler;
import org.briarproject.briar.android.forum.ForumListFragment;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.logout.SignOutFragment;
import org.briarproject.briar.android.navdrawer.NavDrawerController.ExpiryWarning;
import org.briarproject.briar.android.privategroup.list.GroupListFragment;
import org.briarproject.briar.android.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.support.v4.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;
import static android.support.v4.view.GravityCompat.START;
import static android.support.v4.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.briar.android.BriarService.EXTRA_STARTUP_FAILED;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PASSWORD;
import static org.briarproject.briar.android.navdrawer.IntentRouter.handleExternalIntent;
import static org.briarproject.briar.android.navdrawer.NavDrawerController.ExpiryWarning.NO;
import static org.briarproject.briar.android.navdrawer.NavDrawerController.ExpiryWarning.UPDATE;
import static org.briarproject.briar.android.util.UiUtils.getDaysUntilExpiry;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class NavDrawerActivity extends BriarActivity implements
		BaseFragmentListener, TransportStateListener,
		OnNavigationItemSelectedListener {

	private static final Logger LOG =
			getLogger(NavDrawerActivity.class.getName());

	public static Uri CONTACT_URI =
			Uri.parse("briar-content://org.briarproject.briar/contact");
	public static Uri GROUP_URI =
			Uri.parse("briar-content://org.briarproject.briar/group");
	public static Uri FORUM_URI =
			Uri.parse("briar-content://org.briarproject.briar/forum");
	public static Uri BLOG_URI =
			Uri.parse("briar-content://org.briarproject.briar/blog");
	public static Uri CONTACT_ADDED_URI =
			Uri.parse("briar-content://org.briarproject.briar/contact/added");
	public static Uri SIGN_OUT_URI =
			Uri.parse("briar-content://org.briarproject.briar/sign-out");

	private ActionBarDrawerToggle drawerToggle;

	@Inject
	NavDrawerController controller;
	@Inject
	LifecycleManager lifecycleManager;

	private DrawerLayout drawerLayout;
	private NavigationView navigation;

	private List<Transport> transports;
	private BaseAdapter transportsAdapter;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		exitIfStartupFailed(getIntent());
		setContentView(R.layout.activity_nav_drawer);

		Toolbar toolbar = findViewById(R.id.toolbar);
		drawerLayout = findViewById(R.id.drawer_layout);
		navigation = findViewById(R.id.navigation);
		GridView transportsView = findViewById(R.id.transportsView);

		setSupportActionBar(toolbar);
		ActionBar actionBar = requireNonNull(getSupportActionBar());
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
				R.string.nav_drawer_open_description,
				R.string.nav_drawer_close_description);
		drawerLayout.addDrawerListener(drawerToggle);
		navigation.setNavigationItemSelectedListener(this);

		initializeTransports(getLayoutInflater());
		transportsView.setAdapter(transportsAdapter);

		lockManager.isLockable().observe(this, this::setLockVisible);

		if (lifecycleManager.getLifecycleState().isAfter(RUNNING)) {
			showSignOutFragment();
		} else if (state == null) {
			startFragment(ContactListFragment.newInstance(),
					R.id.nav_btn_contacts);
		}
		if (state == null) {
			// do not call this again when there's existing state
			onNewIntent(getIntent());
		}
	}

	@Override
	@SuppressLint("NewApi")
	public void onStart() {
		super.onStart();
		updateTransports();
		lockManager.checkIfLockable();
		controller.showExpiryWarning(new UiResultHandler<ExpiryWarning>(this) {
			@Override
			public void onResultUi(ExpiryWarning expiry) {
				if (expiry != NO) showExpiryWarning(expiry);
			}
		});
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_PASSWORD && result == RESULT_OK) {
			controller.shouldAskForDozeWhitelisting(this,
					new UiResultHandler<Boolean>(this) {
						@Override
						public void onResultUi(Boolean ask) {
							if (ask) {
								showDozeDialog(
										getString(R.string.setup_doze_intro));
							}
						}
					});
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		// will call System.exit()
		exitIfStartupFailed(intent);

		if ("briar-content".equals(intent.getScheme())) {
			handleContentIntent(intent);
		} else {
			handleExternalIntent(this, intent);
		}
	}

	private void handleContentIntent(Intent intent) {
		Uri uri = intent.getData();
		// TODO don't create new instances if they are on the stack (#606)
		if (CONTACT_URI.equals(uri) || CONTACT_ADDED_URI.equals(uri)) {
			startFragment(ContactListFragment.newInstance(),
					R.id.nav_btn_contacts);
		} else if (GROUP_URI.equals(uri)) {
			startFragment(GroupListFragment.newInstance(), R.id.nav_btn_groups);
		} else if (FORUM_URI.equals(uri)) {
			startFragment(ForumListFragment.newInstance(), R.id.nav_btn_forums);
		} else if (BLOG_URI.equals(uri)) {
			startFragment(FeedFragment.newInstance(), R.id.nav_btn_blogs);
		} else if (SIGN_OUT_URI.equals(uri)) {
			signOut(false, false);
		}
	}

	private void exitIfStartupFailed(Intent intent) {
		if (intent.getBooleanExtra(EXTRA_STARTUP_FAILED, false)) {
			finish();
			LOG.info("Exiting");
			System.exit(0);
		}
	}

	private void loadFragment(int fragmentId) {
		// TODO re-use fragments from the manager when possible (#606)
		switch (fragmentId) {
			case R.id.nav_btn_contacts:
				startFragment(ContactListFragment.newInstance());
				break;
			case R.id.nav_btn_groups:
				startFragment(GroupListFragment.newInstance());
				break;
			case R.id.nav_btn_forums:
				startFragment(ForumListFragment.newInstance());
				break;
			case R.id.nav_btn_blogs:
				startFragment(FeedFragment.newInstance());
				break;
			case R.id.nav_btn_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.nav_btn_signout:
				signOut();
				break;
		}
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		drawerLayout.closeDrawer(START);
		clearBackStack();
		if (item.getItemId() == R.id.nav_btn_lock) {
			lockManager.setLocked(true);
			ActivityCompat.finishAfterTransition(this);
			return false;
		} else {
			loadFragment(item.getItemId());
			// Don't display the Settings item as checked
			return item.getItemId() != R.id.nav_btn_settings;
		}
	}

	@Override
	public void onBackPressed() {
		if (drawerLayout.isDrawerOpen(START)) {
			drawerLayout.closeDrawer(START);
		} else {
			FragmentManager fm = getSupportFragmentManager();
			if (fm.findFragmentByTag(SignOutFragment.TAG) != null) {
				finish();
			} else if (fm.getBackStackEntryCount() == 0
					&& fm.findFragmentByTag(ContactListFragment.TAG) == null) {
				/*
				 * This makes sure that the first fragment (ContactListFragment) the
				 * user sees is the same as the last fragment the user sees before
				 * exiting. This models the typical Google navigation behaviour such
				 * as in Gmail/Inbox.
				 */
				startFragment(ContactListFragment.newInstance(),
						R.id.nav_btn_contacts);
			} else {
				super.onBackPressed();
			}
		}
	}

	@Override
	public void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}

	private void showSignOutFragment() {
		drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED);
		startFragment(new SignOutFragment());
	}

	private void signOut() {
		drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED);
		signOut(false, false);
		finish();
	}

	private void startFragment(BaseFragment fragment, int itemId) {
		navigation.setCheckedItem(itemId);
		startFragment(fragment);
	}

	private void startFragment(BaseFragment fragment) {
		if (getSupportFragmentManager().getBackStackEntryCount() == 0)
			startFragment(fragment, false);
		else startFragment(fragment, true);
	}

	private void startFragment(BaseFragment fragment,
			boolean isAddedToBackStack) {
		FragmentTransaction trans =
				getSupportFragmentManager().beginTransaction()
						.setCustomAnimations(R.anim.fade_in,
								R.anim.fade_out, R.anim.fade_in,
								R.anim.fade_out)
						.replace(R.id.fragmentContainer, fragment,
								fragment.getUniqueTag());
		if (isAddedToBackStack) {
			trans.addToBackStack(fragment.getUniqueTag());
		}
		trans.commit();
	}

	private void clearBackStack() {
		getSupportFragmentManager().popBackStackImmediate(null,
				POP_BACK_STACK_INCLUSIVE);
	}

	@Override
	public void handleDbException(DbException e) {
		// Do nothing for now
	}

	private void setLockVisible(boolean visible) {
		MenuItem item = navigation.getMenu().findItem(R.id.nav_btn_lock);
		if (item != null) item.setVisible(visible);
	}

	private void showExpiryWarning(ExpiryWarning expiry) {
		int daysUntilExpiry = getDaysUntilExpiry();
		if (daysUntilExpiry < 0) signOut();

		// show expiry warning text
		ViewGroup expiryWarning = findViewById(R.id.expiryWarning);
		TextView expiryWarningText =
				expiryWarning.findViewById(R.id.expiryWarningText);
		// make close button functional
		ImageView expiryWarningClose =
				expiryWarning.findViewById(R.id.expiryWarningClose);

		// show a different snackbar in green if this is an update
		if (expiry == UPDATE) {
			expiryWarning.setBackgroundColor(
					ContextCompat.getColor(this, R.color.briar_green_light));
			expiryWarningText.setText(
					getString(R.string.expiry_update, daysUntilExpiry));
			expiryWarningText.setTextColor(
					ContextCompat.getColor(this, android.R.color.black));
			expiryWarningClose.setColorFilter(
					ContextCompat.getColor(this, android.R.color.black));
		} else {
			expiryWarningText.setText(getResources()
					.getQuantityString(R.plurals.expiry_warning,
							daysUntilExpiry, daysUntilExpiry));
		}

		expiryWarningClose.setOnClickListener(v -> {
			controller.expiryWarningDismissed();
			expiryWarning.setVisibility(GONE);
		});

		expiryWarning.setVisibility(VISIBLE);
	}

	private void initializeTransports(LayoutInflater inflater) {
		transports = new ArrayList<>(3);

		Transport tor = new Transport();
		tor.id = TorConstants.ID;
		tor.enabled = controller.isTransportRunning(tor.id);
		tor.iconId = R.drawable.transport_tor;
		tor.textId = R.string.transport_tor;
		transports.add(tor);

		Transport bt = new Transport();
		bt.id = BluetoothConstants.ID;
		bt.enabled = controller.isTransportRunning(bt.id);
		bt.iconId = R.drawable.transport_bt;
		bt.textId = R.string.transport_bt;
		transports.add(bt);

		Transport lan = new Transport();
		lan.id = LanTcpConstants.ID;
		lan.enabled = controller.isTransportRunning(lan.id);
		lan.iconId = R.drawable.transport_lan;
		lan.textId = R.string.transport_lan;
		transports.add(lan);

		transportsAdapter = new BaseAdapter() {
			@Override
			public int getCount() {
				return transports.size();
			}

			@Override
			public Transport getItem(int position) {
				return transports.get(position);
			}

			@Override
			public long getItemId(int position) {
				return 0;
			}

			@Override
			public View getView(int position, View convertView,
					ViewGroup parent) {
				View view;
				if (convertView != null) {
					view = convertView;
				} else {
					view = inflater.inflate(R.layout.list_item_transport,
							parent, false);
				}

				Transport t = getItem(position);
				int c;
				if (t.enabled) {
					c = ContextCompat.getColor(NavDrawerActivity.this,
							R.color.briar_green_light);
				} else {
					c = ContextCompat.getColor(NavDrawerActivity.this,
							android.R.color.tertiary_text_light);
				}

				ImageView icon = view.findViewById(R.id.imageView);
				icon.setImageDrawable(ContextCompat
						.getDrawable(NavDrawerActivity.this, t.iconId));
				icon.setColorFilter(c);

				TextView text = view.findViewById(R.id.textView);
				text.setText(getString(t.textId));

				return view;
			}
		};
	}

	@UiThread
	private void setTransport(TransportId id, boolean enabled) {
		if (transports == null || transportsAdapter == null) return;
		for (Transport t : transports) {
			if (t.id.equals(id)) {
				t.enabled = enabled;
				transportsAdapter.notifyDataSetChanged();
				break;
			}
		}
	}

	private void updateTransports() {
		if (transports == null || transportsAdapter == null) return;
		for (Transport t : transports) {
			t.enabled = controller.isTransportRunning(t.id);
		}
		transportsAdapter.notifyDataSetChanged();
	}

	@Override
	public void stateUpdate(TransportId id, boolean enabled) {
		setTransport(id, enabled);
	}

	private static class Transport {

		private TransportId id;
		private boolean enabled;
		private int iconId;
		private int textId;
	}
}
