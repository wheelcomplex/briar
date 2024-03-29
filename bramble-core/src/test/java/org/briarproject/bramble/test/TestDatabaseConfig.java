package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public class TestDatabaseConfig implements DatabaseConfig {

	private final File dbDir, keyDir;

	public TestDatabaseConfig(File testDir) {
		dbDir = new File(testDir, "db");
		keyDir = new File(testDir, "key");
	}

	@Override
	public File getDatabaseDirectory() {
		return dbDir;
	}

	@Override
	public File getDatabaseKeyDirectory() {
		return keyDir;
	}
}
