package com.gustavoas.noti

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gustavoas.noti.model.ProgressBarApp

class ProgressBarAppsRepository private constructor(context: Context) :
    SQLiteOpenHelper(context, "progressBarApps", null, 2) {
    companion object {
        private var instance: ProgressBarAppsRepository? = null

        fun getInstance(context: Context): ProgressBarAppsRepository {
            if (instance == null) {
                instance = ProgressBarAppsRepository(context)
                instance!!.apps = instance!!.getAllApps()
            }
            return instance!!
        }
    }

    private var apps: MutableList<ProgressBarApp> = arrayListOf()

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            "CREATE TABLE IF NOT EXISTS apps (" +
                    "package_name TEXT PRIMARY KEY," +
                    "show_progress INTEGER NOT NULL," +
                    "color INTEGER NOT NULL" +
                    ")"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE apps ADD COLUMN color INTEGER NOT NULL DEFAULT 1")
        }
    }

    fun addApp(app: ProgressBarApp): ProgressBarApp {
        val db = writableDatabase
        db.execSQL("INSERT INTO apps VALUES ('${app.packageName}', ${if (app.showProgressBar) 1 else 0}, ${app.color})")
        db.close()
        apps.add(app)

        return app
    }

    fun updateApp(app: ProgressBarApp) {
        val db = writableDatabase
        db.execSQL("UPDATE apps SET show_progress = ${if (app.showProgressBar) 1 else 0}, color = ${app.color} WHERE package_name = '${app.packageName}'")
        db.close()
        apps[apps.indexOfFirst { it.packageName == app.packageName }] = app
    }

    fun getApp(packageName: String): ProgressBarApp? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM apps WHERE package_name = '$packageName'", null)
        val result = if (cursor.moveToFirst()) {
            val showProgress = cursor.getInt(1)
            val color = cursor.getInt(2)
            ProgressBarApp(packageName, showProgress == 1, color)
        } else {
            null
        }
        cursor.close()
        db.close()
        return result
    }

    fun getAllApps(): MutableList<ProgressBarApp> {
        val apps = mutableListOf<ProgressBarApp>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM apps", null)
        if (cursor.moveToFirst()) {
            do {
                val packageName = cursor.getString(0)
                val showProgress = cursor.getInt(1)
                val color = cursor.getInt(2)
                apps.add(ProgressBarApp(packageName, showProgress == 1, color))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return apps
    }

    fun getAll(): List<ProgressBarApp> {
        return apps
    }
}