package org.briarproject.bramble;

import org.briarproject.bramble.battery.AndroidBatteryModule;
import org.briarproject.bramble.network.AndroidNetworkModule;
import org.briarproject.bramble.plugin.tor.CircumventionModule;
import org.briarproject.bramble.reporting.ReportingModule;
import org.briarproject.bramble.socks.SocksModule;
import org.briarproject.bramble.system.AndroidSystemModule;

import dagger.Module;

@Module(includes = {
		AndroidBatteryModule.class,
		AndroidNetworkModule.class,
		AndroidSystemModule.class,
		CircumventionModule.class,
		ReportingModule.class,
		SocksModule.class
})
public class BrambleAndroidModule {

	public static void initEagerSingletons(BrambleAndroidEagerSingletons c) {
		c.inject(new AndroidBatteryModule.EagerSingletons());
		c.inject(new AndroidNetworkModule.EagerSingletons());
		c.inject(new ReportingModule.EagerSingletons());
	}
}
