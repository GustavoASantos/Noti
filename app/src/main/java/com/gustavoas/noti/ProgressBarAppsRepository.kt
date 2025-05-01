package com.gustavoas.noti

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gustavoas.noti.model.ProgressBarApp

class ProgressBarAppsRepository(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, 3) {
    companion object {
        const val DATABASE_NAME = "progressBarApps"
        const val TABLE_NAME = "apps"
        const val COLUMN_PACKAGE = "package_name"
        const val COLUMN_SHOW_PROGRESS = "show_progress"
        const val COLUMN_COLOR = "color"
        const val COLUMN_USE_DEFAULT = "default_color"
        const val COLUMN_USE_MATERIAL_YOU = "material_you_color"

        private val apps: MutableList<ProgressBarApp> = mutableListOf()
    }

    init {
        if (apps.isEmpty()) {
            apps.addAll(getAllApps())
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME (" +
                    "$COLUMN_PACKAGE TEXT PRIMARY KEY," +
                    "$COLUMN_SHOW_PROGRESS INTEGER NOT NULL DEFAULT 1," +
                    "$COLUMN_COLOR INTEGER DEFAULT NULL," +
                    "$COLUMN_USE_DEFAULT INTEGER NOT NULL DEFAULT 1," +
                    "$COLUMN_USE_MATERIAL_YOU INTEGER NOT NULL DEFAULT 0" +
                    ")"
        )

        val knownApps = listOf(
            "com.google.android.deskclock",
            "com.android.chrome",
            "com.duckduckgo.mobile.android",
            "com.android.vending",
            "com.epicgames.portal",
            "code.name.monkey.retromusic",
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
        )

        knownApps.forEach { packageName ->
            db?.execSQL(
                "INSERT INTO $TABLE_NAME ($COLUMN_PACKAGE) VALUES ('$packageName')"
            )
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_COLOR INTEGER NOT NULL DEFAULT 1")
        }

        if (oldVersion < 3) {
            db?.execSQL("ALTER TABLE $TABLE_NAME RENAME TO ${TABLE_NAME}_old")

            db?.execSQL(
                "CREATE TABLE $TABLE_NAME (" +
                        "$COLUMN_PACKAGE TEXT PRIMARY KEY," +
                        "$COLUMN_SHOW_PROGRESS INTEGER NOT NULL DEFAULT 1," +
                        "$COLUMN_COLOR INTEGER DEFAULT NULL," +
                        "$COLUMN_USE_DEFAULT INTEGER NOT NULL DEFAULT 1," +
                        "$COLUMN_USE_MATERIAL_YOU INTEGER NOT NULL DEFAULT 0" +
                        ")"
            )

            db?.execSQL(
                "INSERT INTO $TABLE_NAME ($COLUMN_PACKAGE, $COLUMN_SHOW_PROGRESS, $COLUMN_COLOR) " +
                        "SELECT $COLUMN_PACKAGE, $COLUMN_SHOW_PROGRESS, $COLUMN_COLOR FROM ${TABLE_NAME}_old"
            )

            db?.execSQL("DROP TABLE ${TABLE_NAME}_old")

            db?.execSQL("UPDATE $TABLE_NAME SET " +
                    "$COLUMN_USE_DEFAULT = CASE WHEN $COLUMN_COLOR = 1 THEN 1 ELSE 0 END, " +
                    "$COLUMN_USE_MATERIAL_YOU = CASE WHEN $COLUMN_COLOR = 2 THEN 1 ELSE 0 END, " +
                    "$COLUMN_COLOR = CASE WHEN $COLUMN_COLOR IN (1, 2) THEN NULL ELSE $COLUMN_COLOR END"
            )
        }
    }

    fun addApp(app: ProgressBarApp): ProgressBarApp {
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO $TABLE_NAME ($COLUMN_PACKAGE) VALUES (?)",
            arrayOf(app.packageName)
        )

        apps.firstOrNull {
            it.packageName == app.packageName
        }?.let {
            return it
        }

        apps.add(app)

        return app
    }

    fun updateApp(app: ProgressBarApp) {
        writableDatabase.execSQL(
            "UPDATE $TABLE_NAME " +
                    "SET $COLUMN_SHOW_PROGRESS = ?, " +
                    "$COLUMN_COLOR = ?, " +
                    "$COLUMN_USE_DEFAULT = ?, " +
                    "$COLUMN_USE_MATERIAL_YOU = ? " +
                    "WHERE $COLUMN_PACKAGE = ?",
            arrayOf(
                if (app.showProgressBar) 1 else 0,
                app.color,
                if (app.useDefaultColor) 1 else 0,
                if (app.useMaterialYouColor) 1 else 0,
                app.packageName
            )
        )

        apps.indexOfFirst {
            it.packageName == app.packageName
        }.let { index ->
            if (index != -1) {
                apps[index] = app
            }
        }
    }

    fun showProgressForApp(packageName: String): Boolean? {
        apps.firstOrNull {
            it.packageName == packageName
        }?.let {
            return it.showProgressBar
        }

        readableDatabase.rawQuery(
            "SELECT $COLUMN_SHOW_PROGRESS FROM $TABLE_NAME WHERE $COLUMN_PACKAGE = ?",
            arrayOf(packageName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SHOW_PROGRESS)) == 1
            }
        }

        return null
    }

    fun getApp(packageName: String): ProgressBarApp? {
        apps.firstOrNull {
            it.packageName == packageName
        }?.let {
            return it
        }

        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_PACKAGE = ?",
            arrayOf(packageName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val showProgress = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SHOW_PROGRESS))
                val colorIndex = cursor.getColumnIndexOrThrow(COLUMN_COLOR)
                val color = if (cursor.isNull(colorIndex)) {
                    null
                } else {
                    cursor.getInt(colorIndex)
                }
                val useDefault = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USE_DEFAULT))
                val useMaterialYou = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USE_MATERIAL_YOU))
                return ProgressBarApp(
                    packageName,
                    showProgress == 1,
                    color,
                    useDefault == 1,
                    useMaterialYou == 1
                )
            }
        }

        return null
    }

    private fun getAllApps(): MutableList<ProgressBarApp> {
        val apps = mutableListOf<ProgressBarApp>()
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_NAME", null
        ).use { cursor ->
            val packageIndex = cursor.getColumnIndexOrThrow(COLUMN_PACKAGE)
            val showProgressIndex = cursor.getColumnIndexOrThrow(COLUMN_SHOW_PROGRESS)
            val colorIndex = cursor.getColumnIndexOrThrow(COLUMN_COLOR)
            val useDefaultIndex = cursor.getColumnIndexOrThrow(COLUMN_USE_DEFAULT)
            val useMaterialYouIndex = cursor.getColumnIndexOrThrow(COLUMN_USE_MATERIAL_YOU)

            while (cursor.moveToNext()) {
                val packageName = cursor.getString(packageIndex)
                val showProgress = cursor.getInt(showProgressIndex)
                val color = if (cursor.isNull(colorIndex)) {
                    null
                } else {
                    cursor.getInt(colorIndex)
                }
                val useDefault = cursor.getInt(useDefaultIndex)
                val useMaterialYou = cursor.getInt(useMaterialYouIndex)
                apps.add(
                    ProgressBarApp(
                        packageName,
                        showProgress == 1,
                        color,
                        useDefault == 1,
                        useMaterialYou == 1
                    )
                )
            }
        }

        return apps
    }

    fun getAll(): List<ProgressBarApp> {
        return apps.toList()
    }
}