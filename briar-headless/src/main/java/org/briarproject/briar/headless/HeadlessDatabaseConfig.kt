package org.briarproject.briar.headless

import org.briarproject.bramble.api.db.DatabaseConfig
import java.io.File

internal class HeadlessDatabaseConfig(private val dbDir: File, private val keyDir: File) :
    DatabaseConfig {

    override fun getDatabaseDirectory(): File {
        return dbDir
    }

    override fun getDatabaseKeyDirectory(): File {
        return keyDir
    }
}
