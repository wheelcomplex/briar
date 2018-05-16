package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.os.Build;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.util.AndroidUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.net.SocketFactory;

@Immutable
@NotNullByDefault
public class TorPluginFactory implements DuplexPluginFactory {

	private static final Logger LOG =
			Logger.getLogger(TorPluginFactory.class.getName());

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds

	private final Executor ioExecutor;
	private final ScheduledExecutorService scheduler;
	private final Context appContext;
	private final LocationUtils locationUtils;
	private final EventBus eventBus;
	private final SocketFactory torSocketFactory;
	private final CircumventionProvider circumventionProvider;
	private final Clock clock;

	public TorPluginFactory(Executor ioExecutor,
			ScheduledExecutorService scheduler, Context appContext,
			LocationUtils locationUtils, EventBus eventBus,
			SocketFactory torSocketFactory,
			CircumventionProvider circumventionProvider, Clock clock) {
		this.ioExecutor = ioExecutor;
		this.scheduler = scheduler;
		this.appContext = appContext;
		this.locationUtils = locationUtils;
		this.eventBus = eventBus;
		this.torSocketFactory = torSocketFactory;
		this.clock = clock;
		this.circumventionProvider = circumventionProvider;
	}

	@Override
	public TransportId getId() {
		return TorConstants.ID;
	}

	@Override
	public int getMaxLatency() {
		return MAX_LATENCY;
	}

	@Override
	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {

		// Check that we have a Tor binary for this architecture
		String architecture = null;
		for (String abi : AndroidUtils.getSupportedArchitectures()) {
			if (abi.startsWith("x86")) {
				architecture = "x86";
				break;
			} else if (abi.startsWith("armeabi")) {
				architecture = "arm";
				break;
			}
		}
		if (architecture == null) {
			LOG.info("Tor is not supported on this architecture");
			return null;
		}
		// Use position-independent executable for SDK >= 16
		if (Build.VERSION.SDK_INT >= 16) architecture += "_pie";

		TorPlugin plugin = new TorPlugin(ioExecutor, scheduler, appContext,
				locationUtils, torSocketFactory, clock, callback, architecture,
				circumventionProvider, MAX_LATENCY, MAX_IDLE_TIME);
		eventBus.addListener(plugin);
		return plugin;
	}
}
