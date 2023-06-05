package com.example.testapplication

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler
import java.text.DateFormat
import java.util.Date


class Application: Application() {

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(this))
    }

}

class ExceptionHandler(  myContext: Context) : UncaughtExceptionHandler {
    private val LINE_SEPARATOR = "\n"
    var defaultUEH: UncaughtExceptionHandler
    val logDirectory = "${myContext.getExternalFilesDir(null)}/logs"

    init {
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
    }

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        val stackTrace = StringWriter()
        exception.printStackTrace(PrintWriter(stackTrace))
        val errorReport = StringBuilder()
        errorReport.append("************ CAUSE OF ERROR ************\n\n")
        errorReport.append(stackTrace.toString())
        errorReport.append("\n************ DEVICE INFORMATION ***********\n")
        errorReport.append("Brand: ")
        errorReport.append(Build.BRAND)
        errorReport.append(LINE_SEPARATOR)
        errorReport.append("Device: ")
        errorReport.append(Build.DEVICE)
        errorReport.append(LINE_SEPARATOR)
        errorReport.append("Model: ")
        errorReport.append(Build.MODEL)
        errorReport.append(LINE_SEPARATOR)
        errorReport.append("Version OS: ")
        errorReport.append(Build.VERSION.RELEASE)
        errorReport.append(LINE_SEPARATOR)
        val root = Environment.getExternalStorageDirectory();
            //logDirectory
        val currentDateTimeString: String = DateFormat.getDateTimeInstance().format(
            Date()
        )
        val dir = File(root.absolutePath + "/Logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "log.txt")
        try {
            val buf = BufferedWriter(FileWriter(file, true))
            buf.append("$currentDateTimeString:$errorReport")
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        defaultUEH.uncaughtException(thread, exception)
        //System.exit(0)
    }

}