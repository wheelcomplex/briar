package org.briarproject.bramble.test;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		TestDatabaseConfigModule.class,
		TestPluginConfigModule.class,
		TestSecureRandomModule.class
})
public class BrambleCoreIntegrationTestModule {

	@Provides
	FeatureFlags provideFeatureFlags() {
		return new FeatureFlags() {

			@Override
			public boolean shouldEnableImageAttachments() {
				return true;
			}

			@Override
			public boolean shouldEnableRemoteContacts() {
				return true;
			}
		};
	}
}
