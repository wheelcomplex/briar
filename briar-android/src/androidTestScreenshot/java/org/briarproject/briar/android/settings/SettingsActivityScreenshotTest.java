package org.briarproject.briar.android.settings;

import android.content.Intent;
import android.support.test.espresso.contrib.DrawerActions;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.android.ScreenshotTest;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.contrib.RecyclerViewActions.scrollTo;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isEnabled;
import static android.support.test.espresso.matcher.ViewMatchers.withChild;
import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.briar.android.ViewActions.waitUntilMatches;
import static org.hamcrest.CoreMatchers.is;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityScreenshotTest extends ScreenshotTest {

	@Rule
	public ActivityTestRule<SettingsActivity> testRule =
			new ActivityTestRule<>(SettingsActivity.class);

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	@Test
	public void changeTheme() {
		onView(withText(R.string.settings_button))
				.check(matches(isDisplayed()));

		screenshot("manual_dark_theme_settings", testRule.getActivity());

		// switch to dark theme
		onView(withText(R.string.pref_theme_title))
				.check(matches(isDisplayed()))
				.perform(click());
		onView(withText(R.string.pref_theme_dark))
				.check(matches(isDisplayed()))
				.perform(click());

		openNavDrawer();

		screenshot("manual_dark_theme_nav_drawer", testRule.getActivity());

		// switch to back to light theme
		onView(withText(R.string.settings_button))
				.check(matches(isDisplayed()))
				.perform(click());
		onView(withText(R.string.pref_theme_title))
				.check(matches(isDisplayed()))
				.perform(click());
		onView(withText(R.string.pref_theme_light))
				.check(matches(isDisplayed()))
				.perform(click());
	}

	@Test
	public void appLock() {
		// scroll down
		onView(withClassName(is(RecyclerView.class.getName())))
				.perform(scrollTo(hasDescendant(
						// scroll down a bit more to have settings in the middle
						withText(R.string.panic_setting))));

		// wait for settings to get loaded and enabled
		onView(withText(R.string.tor_mobile_data_title))
				.perform(waitUntilMatches(isEnabled()));

		// ensure app lock is displayed and enabled
		onView(withText(R.string.pref_lock_title))
				.check(matches(isDisplayed()))
				.check(matches(isEnabled()))
				.perform(click());
		onView(withChild(withText(R.string.pref_lock_timeout_title)))
				.check(matches(isDisplayed()))
				.check(matches(isEnabled()));

		screenshot("manual_app_lock", testRule.getActivity());

		openNavDrawer();

		screenshot("manual_app_lock_nav_drawer", testRule.getActivity());
	}

	@Test
	public void torSettings() {
		// scroll down
		onView(withClassName(is(RecyclerView.class.getName())))
				.perform(scrollTo(hasDescendant(
						// scroll down a bit more to have settings in the middle
						withText(R.string.pref_lock_timeout_title))));

		// wait for settings to get loaded and enabled
		onView(withText(R.string.tor_network_setting))
				.check(matches(isDisplayed()))
				.perform(waitUntilMatches(isEnabled()));

		screenshot("manual_tor_settings", testRule.getActivity());
	}

	private void openNavDrawer() {
		// start main activity
		Intent i =
				new Intent(testRule.getActivity(), NavDrawerActivity.class);
		testRule.getActivity().startActivity(i);

		// open navigation drawer
		onView(withId(R.id.drawer_layout))
				.check(matches(isClosed(Gravity.START)))
				.perform(DrawerActions.open());
	}

}
