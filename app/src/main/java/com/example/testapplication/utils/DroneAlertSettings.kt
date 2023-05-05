package com.example.testapplication.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

@SuppressLint("SdCardPath")
class DroneAlertSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(this.javaClass.simpleName, Context.MODE_PRIVATE)

    var connectionType: Int by SharedPrefIntegerProperty(Bluetooth)


    companion object {
        const val Bluetooth = 0
    }

    /**
     * SharedPrefIntegerProperty
     */
    private class SharedPrefIntegerProperty(private val def: Int) {
        operator fun getValue(sharedPrefHandler: DroneAlertSettings, property: KProperty<*>): Int {
            return if (sharedPrefHandler.prefs.contains(property.name))
                return sharedPrefHandler.prefs.getInt(property.name, 0)
            else
                def
        }

        operator fun setValue(sharedPrefHandler: DroneAlertSettings, property: KProperty<*>, i: Int) {
            sharedPrefHandler.prefs.edit().putInt(property.name, i).apply()
        }
    }

    /**
     * SharedPrefStringProperty
     */
    private class SharedPrefStringProperty(private val def: String) {
        operator fun getValue(sharedPrefHandler: DroneAlertSettings, property: KProperty<*>): String {
            return if (sharedPrefHandler.prefs.contains(property.name))
                sharedPrefHandler.prefs.getString(property.name, def) ?: def
            else
                def
        }

        operator fun setValue(sharedPrefHandler: DroneAlertSettings, property: KProperty<*>, s: String) {
            sharedPrefHandler.prefs.edit().putString(property.name, s).apply()
        }
    }
}