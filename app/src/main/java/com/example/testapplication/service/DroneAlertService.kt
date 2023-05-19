package com.example.testapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import com.example.testapplication.R
import com.felhr.usbserial.UsbSerialDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class DroneAlertService: Service() {

    private var job: Job? = null
    var _start: Long = 0
    var _stop: Long = 0
    var _step: Long = 0
    var request = 0
    var graphCounter = 1
    var maxGraphCounter = 5
    var checkConnect = false
    var delay = 150L
    private var _attenuation: Long = 0
    private var _pointIndex = 0
    private var _pointShift = 0
    private var _lastPointId = 0
    private var _messageIndex = 0
    private val _message = ByteArray(2048)
    var check = true
    var recordCheck = false
    var soundType = 0

    private val broadcast: BroadcastReceiver = object: BroadcastReceiver() {
        lateinit var startList: List<Long>
        lateinit var stopList: List<Long>
        var step by Delegates.notNull<Long>()
        override fun onReceive(context: Context?, intent: Intent) {
            //startList = intent.getLongArrayExtra(START_LIST)!!.toList()
            //stopList = intent.getLongArrayExtra(STOP_LIST)!!.toList()
            step = intent.getLongExtra(STEP,1000000L)
            //startScan(startList,stopList)
            println(step)
            stopSelf()

        }

    }

    override fun onBind(intent: Intent?) : IBinder? = null

    override fun onCreate() {
        //Timber.d(" <= Сервис печати: создание...")
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val CHANNEL_ID = "my_channel_01"
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(R.id.service, notification)
        }
        val filter = IntentFilter()
        filter.addAction(SET_DATA)
        registerReceiver(broadcast, filter)
        //Timber.d(" <= Сервис печати: успешно создан")
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Timber.d(" <= Сервис печати: запуск $intent, flags: $flags, startId: $startId")
        mIsServiceRunning = true
        handlerThread = HandlerThread("MyLocationThread")
        handlerThread!!.isDaemon = true
        handlerThread!!.start()
        handler =  Handler(handlerThread!!.looper)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        //Timber.d(" <= Сервис печати: остановка...")
        try {
            mIsServiceRunning = false
            unregisterReceiver(broadcast)
            handlerThread!!.quit()
            super.onDestroy()
        } catch (e: Exception) {
            //Timber.e(e, "onDestroy error")
        }
        //Timber.d(" <= Сервис печати: успешно остановлен")
    }



    companion object {
        const val SET_DATA = "data for scan"
        //const val START_LIST = "START_LIST_EXTRA"
       // const val STOP_LIST = "STOP_LIST_EXTRA"
        const val STEP = "STEP_EXTRA"
        private var mIsServiceRunning = false

        val isScanServiceRunning: Boolean
            get() {
                //Timber.d("mIsServiceRunning = %s", mIsServiceRunning)
                return mIsServiceRunning
            }

        private const val _commandPattern = "scn %1\$d %2\$d %3\$d %4\$d %5\$d \r\n"

        // scn  - scan command
        // %1$d - start frequency, long value in Hz
        // %2$d - stop frequency, long value in Hz
        // %3$d - metering step, long value in Hz
        // %4$d - attenuation, calculated value (value = (BASE_ATTENUATION_CALCULATION_LEVEL * ATTENUATION_ACCURACY_COEFFICIENT) + attenuation_in_dB * ATTENUATION_ACCURACY_COEFFICIENT)
        // %5$d - command id (integer value, used for response identification)
        private const val BASE_ATTENUATION_CALCULATION_LEVEL =
            100 // for avoid negative values (100 = 0 dB, 120 = 20 dB, 70 = -30 dB)
        private const val ATTENUATION_ACCURACY_COEFFICIENT = 100 // two decimal places
        private const val BASE_AMPLITUDE_CALCULATION_LEVEL = 80 // for avoid negative values

        // amplitudeIntValue = 18600 => amplitude = ((80 * 10.0 - 18659)) / 10.0 = -108.59 dB
        private const val AMPLITUDE_ACCURACY_COEFFICIENT = 10.0 // one decimal place
        private const val _intermediateFrequency = 500000L
        var serialVM: UsbSerialDevice? = null
    }

    fun startScan(startList: List<Long>, stopList: List<Long>) {
        job = CoroutineScope(Dispatchers.IO).launch {
            //delay(100L)
            while (check) {
                if (request == graphCounter)
                    request = 0
                _start = startList[request]
                _stop = stopList[request]
                //= 2400000000L // 2400 MHz
                //= 2500000000L // 2499 MHz
                //_step = 500000L //  500 KHz
                _attenuation = 0 //    0 dB
                val attenuation =
                    BASE_ATTENUATION_CALCULATION_LEVEL * ATTENUATION_ACCURACY_COEFFICIENT + _attenuation * ATTENUATION_ACCURACY_COEFFICIENT
                _lastPointId = 0
                _pointShift = 0
                val command = String.format(
                    _commandPattern,
                    _start,
                    _stop,
                    _step,
                    attenuation,
                    0 // command id (can be a random integer value)
                )
                serialVM?.write(command.toByteArray())
                delay(150L)
            }
        }
        job!!.start()
    }
}