package org.briarproject.briar.android.activity;

import android.app.Activity;

import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.controller.BriarControllerImpl;
import org.briarproject.briar.android.controller.DbController;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.login.ChangePasswordController;
import org.briarproject.briar.android.login.ChangePasswordControllerImpl;
import org.briarproject.briar.android.account.SetupController;
import org.briarproject.briar.android.account.SetupControllerImpl;
import org.briarproject.briar.android.navdrawer.NavDrawerController;
import org.briarproject.briar.android.navdrawer.NavDrawerControllerImpl;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.android.BriarService.BriarServiceConnection;

@Module
public class ActivityModule {

	private final BaseActivity activity;

	public ActivityModule(BaseActivity activity) {
		this.activity = activity;
	}

	@ActivityScope
	@Provides
	BaseActivity provideBaseActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	Activity provideActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	SetupController provideSetupController(
			SetupControllerImpl setupController) {
		return setupController;
	}

	@ActivityScope
	@Provides
	ChangePasswordController providePasswordController(
			ChangePasswordControllerImpl passwordController) {
		return passwordController;
	}

	@ActivityScope
	@Provides
	protected BriarController provideBriarController(
			BriarControllerImpl briarController) {
		activity.addLifecycleController(briarController);
		return briarController;
	}

	@ActivityScope
	@Provides
	DbController provideDBController(DbControllerImpl dbController) {
		return dbController;
	}

	@ActivityScope
	@Provides
	NavDrawerController provideNavDrawerController(
			NavDrawerControllerImpl navDrawerController) {
		activity.addLifecycleController(navDrawerController);
		return navDrawerController;
	}

	@ActivityScope
	@Provides
	BriarServiceConnection provideBriarServiceConnection() {
		return new BriarServiceConnection();
	}

}
