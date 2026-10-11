package com.bestcallerapp.smswebhook

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bestcallerapp.smswebhook.data.local.database.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the exported schema can be created. When the schema version is bumped, add a test
 * here that creates the previous version and runs [AppDatabase.MIGRATIONS] with validation.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun createLatestSchema() {
        helper.createDatabase(TEST_DB, AppDatabase.VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).close()
    }

    private companion object {
        const val TEST_DB = "schema-test"
    }
}
