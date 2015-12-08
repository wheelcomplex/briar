package org.briarproject;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.junit.Assert.fail;

public abstract class BriarTestCase {

	public BriarTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = new UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable throwable) {
				fail();
			}
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}
}
