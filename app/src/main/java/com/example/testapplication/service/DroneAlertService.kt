package com.example.testapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import com.example.testapplication.R
import com.example.testapplication.ml.Model
import com.example.testapplication.utils.DroneAlertSettings
import com.felhr.usbserial.UsbSerialDevice
import com.jjoe64.graphview.series.DataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.properties.Delegates

class DroneAlertService: Service() {

    private var job: Job? = null
    private var convertJob: Job? = null
    lateinit var mediaPlayer:MediaPlayer
    var model: Model? = null
    lateinit var vibration: Vibrator
    var _start: Long = 0
    var _stop: Long = 0
    var request = 0
    var graphCounter = 1
    var maxGraphCounter = 5
    var checkConnect = false
    var delay = 150L
    var counter = 0
    private var _attenuation: Long = 0
    private var _pointIndex = 0
    private var _pointShift = 0
    private var _lastPointId = 0
    private var _messageIndex = 0
    private val _message = ByteArray(2048)
    var listCoordinates = mutableListOf<DataPoint>()
    var check = true
    var recordCheck = false
    var soundType = 0

    private val broadcast: BroadcastReceiver = object: BroadcastReceiver() {
        lateinit var _startList: List<Long>
        lateinit var _stopList: List<Long>
        var step by Delegates.notNull<Long>()
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == SET_DATA) {
                _startList = intent.getLongArrayExtra(START_LIST)!!.toList()
                _stopList = intent.getLongArrayExtra(STOP_LIST)!!.toList()
                val setting = context?.let { DroneAlertSettings(it) }
                soundType = setting!!.connectionType
                step = intent.getLongExtra(STEP, 1000000L)
                graphCounter = intent.getIntExtra(GRAPHCOUNTER,1)
                _step = step
                starList = _startList as MutableList<Long>
                stopList = _stopList as MutableList<Long>
                mediaPlayer = if (soundType == 1) MediaPlayer.create(context, R.raw.alarmbuzzer)
                else MediaPlayer.create(context, R.raw.sirena)
                model = context.let { Model.newInstance(it) }
                vibration = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (checkSA6)
                    startScanSA6()
                else startScan()
                read()
                convertToBitmap()
            } else {
                job?.cancel()
                convertJob?.cancel()
                model?.close()
                counter = 0
                request = 0
                serialVM?.close()
            }

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
        filter.addAction(STOP_SCAN)
        registerReceiver(broadcast, filter)
        println(" <= Сервис скана: успешно создан")
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println(" <= Сервис скана: запуск $intent, flags: $flags, startId: $startId")
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
        const val STOP_SCAN = "stop scan"
        const val SET_DATA = "data for scan"
        const val GET_DATA = "get data"
        const val START_LIST = "START_LIST_EXTRA"
        const val STOP_LIST = "STOP_LIST_EXTRA"
        const val STEP = "STEP_EXTRA"
        const val GRAPHCOUNTER = "GRAPH_COUNTER"
        const val X_COORDINATS = "X_COORDINATS"
        const val Y_COORDINATS = "Y_COORDINATS"
        const val START = "START"
        const val STOP = "STOP"
        const val COUNTER = "COUNTER"
        private var mIsServiceRunning = false
        var imageList = mutableListOf<ImageView>()

        val isScanServiceRunning: Boolean
            get() {
                //Timber.d("mIsServiceRunning = %s", mIsServiceRunning)
                return mIsServiceRunning
            }

        private const val _commandPattern = "scn %1\$d %2\$d %3\$d %4\$d %5\$d \r\n"
        private const val _commandPatternSA6 = "scn20 %1\$d %2\$d %3\$d %4\$d %5\$d %6\$d %7\$d\r\n"

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
        var serialVMList = mutableListOf<UsbSerialDevice?>()
        var serialVM: UsbSerialDevice? = null
        var starList = mutableListOf<Long>()
        var stopList = mutableListOf<Long>()
        var _step: Long = 0
        var coord2 = mutableListOf<MutableList<List<DataPoint>>>()
        var imageCounter = 0
        var checkSA6 = false
    }

    fun startScan() {
        job = CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            //delay(100L)
            while (check) {
                if (request == graphCounter)
                    request = 0
                _start = starList[request]
                _stop = stopList[request]
                //= 2400000000L // 2400 MHz
                //= 2500000000L // 2499 MHz
                //_step = 500000L //  500 KHz
                _attenuation = 0 //    0 dB
                val attenuation =
                    BASE_ATTENUATION_CALCULATION_LEVEL * ATTENUATION_ACCURACY_COEFFICIENT + _attenuation * ATTENUATION_ACCURACY_COEFFICIENT
                _lastPointId = 0
                _pointShift = 0
                listCoordinates.clear()
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

    fun startScanSA6() {
        job = CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            //delay(100L)
            while (check) {
                if (request == graphCounter)
                    request = 0
                _start = starList[request]
                _stop = stopList[request]
                //= 2400000000L // 2400 MHz
                //= 2500000000L // 2499 MHz
                //_step = 500000L //  500 KHz
                _attenuation = 0 //    0 dB
                val attenuation =
                    BASE_ATTENUATION_CALCULATION_LEVEL * ATTENUATION_ACCURACY_COEFFICIENT + _attenuation * ATTENUATION_ACCURACY_COEFFICIENT
                _lastPointId = 0
                _pointShift = 0
                listCoordinates.clear()
                val command = String.format(
                    _commandPatternSA6,
                    _start,
                    _stop,
                    _step,
                    200,
                    20,
                    10700000,
                    attenuation
                    // command id (can be a random integer value)
                )
                serialVM?.write(command.toByteArray())
                delay(200L)
            }
        }
        job!!.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun read() {
        serialVM?.read {
            val buffer = it
            if (buffer == null) {
                return@read
            }
            for (charData in buffer) {
                if (_messageIndex >= 1 && _message[_messageIndex - 1].toInt() == 13 && charData.toInt() == 10) {
                    //if (_streamReceiver != null) {
                    val dst = ByteArray(_messageIndex - 1)
                    System.arraycopy(_message, 0, dst, 0, _messageIndex - 1)
                    processDeviceResponse(dst)
                    //}
                    for (j in 0 until _messageIndex) {
                        _message[j] = 0
                    }
                    _messageIndex = 0
                } else {
                    if (_messageIndex < _message.size) {
                        _message[_messageIndex++] = charData
                    } else {
                        for (j in 0 until _messageIndex) {
                            _message[j] = 0
                        }
                        _messageIndex = 0
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun processDeviceResponse(message: ByteArray) {
        val messageLength = message.size
        if (messageLength == 2 || messageLength == 6) {
            processMessage(message)
        } else {
            try {
                if (messageLength > 100) {
                    val q = (_stop - _start) / _step * 2
                    val w = messageLength - q
                    test(message, w.toInt())

                } else {
                    val readMessage = message.let { String(it, 0, message.size) }
                    processMessage(readMessage)
                }
            } catch (e:Exception) {
                println(e)
            }
        }
    }

    private fun test(message: ByteArray, size: Int) {
        var i = 0
        var k = 0
        while (i != message.size - size) {
            if (_start < 5000000000L) {
                if (message[i] < 0) message[i] = (message[i] + 128).toByte()
                if (message[i + 1] < 0) message[i + 1] = (message[i + 1] + 128).toByte()
            }
            val qwe = message[i].toInt() shl 8 or message[i + 1].toInt()
            val data = qwe  and 2047
            val amplitude = (BASE_AMPLITUDE_CALCULATION_LEVEL * AMPLITUDE_ACCURACY_COEFFICIENT - data) / AMPLITUDE_ACCURACY_COEFFICIENT - _attenuation
            val frequency = _start + _step * k
            receiveStreamData(frequency, amplitude)
            k++
            i+= 2
        }
    }

    fun processMessage(message: ByteArray) {
        val messageLength = message.size
        if (messageLength == 6) {
            _pointIndex =
                message[0].toInt() shl 24 or (message[1].toInt() shl 16) or (message[2].toInt() shl 8) or message[3].toInt()
            // pointIndex is calculated, but sometimes send to avoid possible data loss errors
        }
        val pointId = message[messageLength - 2].toInt() and 0x000000FF shr 3 // 1111 1000 0000 0000
        val amplitudeIntValue =
            message[messageLength - 2].toInt() and 0x00000007 shl 8 or (message[messageLength - 1].toInt() and 0x000000FF) // 0000 0111 1111 1111
        val frequency =
            _start + _pointShift * _step + _intermediateFrequency * 2 * _pointIndex // result is returned not in a row
        // 100000000000000000000000     result return step = intermediate frequency
        // 100000001000000000000000
        // 100000001000000010000000
        // new level
        // 110000001000000010000000
        // 110000001100000010000000
        // 110000001100000011000000
        // new level
        // 111000001100000011000000
        // etc
        val amplitude =
            (BASE_AMPLITUDE_CALCULATION_LEVEL * AMPLITUDE_ACCURACY_COEFFICIENT - amplitudeIntValue) / AMPLITUDE_ACCURACY_COEFFICIENT - _attenuation
        // amplitudeIntValue = 18600 => amplitude = ((80 * 10.0 - 18659)) / 10.0 = -108.59 dB
        if (_lastPointId < pointId || pointId == 0) {
            receiveStreamData(frequency, amplitude)
            _lastPointId = pointId
            _pointIndex++
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun processMessage(readMessage: String) {
        if (readMessage.contentEquals("l")) {
            _pointShift++
            _pointIndex = 0
        }  else if (readMessage.contentEquals("complete")) {
            val list = listCoordinates.sortedBy { it.x }
            val xCoordinates = mutableListOf<Double>()
            val yCoordinates = mutableListOf<Double>()
            val intent = Intent()
            try {
                if (list.size == (((stopList[request] - starList[request]) / _step).toInt() +
                            ((stopList[request] - starList[request]) / 1000000L).toInt() + 1) && _step == 500000L
                ) {
                    try {
                        if (list[0].x == (starList[request] / 1000000L).toDouble()) {
                            list.forEach{
                                xCoordinates.add(it.x)
                                yCoordinates.add(it.y)
                            }
                            intent.action = GET_DATA
                            intent.putExtra(X_COORDINATS, xCoordinates.toDoubleArray())
                            intent.putExtra(Y_COORDINATS, yCoordinates.toDoubleArray())
                            intent.putExtra(COUNTER, request)
                            intent.putExtra(START, starList[request])
                            intent.putExtra(STOP, stopList[request])
                            applicationContext.sendBroadcast(intent)
                            coord2[request].add(0, list)
                            /*if (recordCheck) {
                                recordList.add(0, list)
                            }*/
                            val listCoordinates = coord2[request]
                            //convertToBitmap(listCoordinates)
                            onCommandComplete()
                        }
                    } catch (e: Exception) {
                        println(e)
                    }
                } else if (list.size == (((stopList[request] - starList[request]) / _step).toInt() +
                            ((stopList[request] - starList[request]) / 1000000L).toInt() + 1) && _step == 1000000L
                ) {
                    try {
                        if (list[0].x == (starList[request] / 1000000L).toDouble()) {
                            /*if (recordCheck) {
                                recordList.add(0, list)
                            }*/
                            list.forEach{
                                xCoordinates.add(it.x)
                                yCoordinates.add(it.y)
                            }
                            intent.action = GET_DATA
                            intent.putExtra(X_COORDINATS, xCoordinates.toDoubleArray())
                            intent.putExtra(Y_COORDINATS, yCoordinates.toDoubleArray())
                            intent.putExtra(COUNTER, request)
                            intent.putExtra(START, starList[request])
                            intent.putExtra(STOP, stopList[request])
                            applicationContext.sendBroadcast(intent)
                            coord2[request].add(0, list)
                            //liveDataCoordinates.postValue(list)
                            val listCoordinates = coord2[request]
                            //convertToBitmap(listCoordinates)
                            onCommandComplete()
                        }
                    } catch (e: Exception) {
                        println(e)
                    }
                } else if (checkSA6 && list.size == ((_stop - _start) / _step).toInt()) {
                try {
                //if (recordCheck) {
                //    recordList.add(0,list)
                //}
                    list.forEach{
                        xCoordinates.add(it.x)
                        yCoordinates.add(it.y)
                    }
                    intent.action = GET_DATA
                    intent.putExtra(X_COORDINATS, xCoordinates.toDoubleArray())
                    intent.putExtra(Y_COORDINATS, yCoordinates.toDoubleArray())
                    intent.putExtra(COUNTER, request)
                    intent.putExtra(START, starList[request])
                    intent.putExtra(STOP, stopList[request])
                    applicationContext.sendBroadcast(intent)
                    coord2[request].add(0, list)
                //liveDataCoordinates.postValue(list)
                val listCoordinates = coord2[request]
                //convertToBitmap(listCoordinates)
                onCommandComplete()
                } catch (e:Exception) {
                    println(e)
                }
            } else if (list.size == (((stopList[request] - starList[request]) / _step).toInt() +
                            ((stopList[request] - starList[request]) / 1000000L).toInt() + 1) && _step == 250000L
                ) {
                    try {
                        /*if (recordCheck) {
                            recordList.add(0, list)
                        }*/
                        list.forEach{
                            xCoordinates.add(it.x)
                            yCoordinates.add(it.y)
                        }
                        intent.action = GET_DATA
                        intent.putExtra(X_COORDINATS, xCoordinates.toDoubleArray())
                        intent.putExtra(Y_COORDINATS, yCoordinates.toDoubleArray())
                        intent.putExtra(COUNTER, request)
                        intent.putExtra(START, starList[request])
                        intent.putExtra(STOP, stopList[request])
                        applicationContext.sendBroadcast(intent)
                        coord2[request].add(0, list)
                        onCommandComplete()
                    } catch (e: Exception) {
                        println(e)
                    }
                } else if (list.size == (((stopList[request] - starList[request]) / _step).toInt() +
                            ((stopList[request] - starList[request]) / 1000000L).toInt() + 1) && _step == 100000L
                ) {
                    try {
                        list.forEach{
                            xCoordinates.add(it.x)
                            yCoordinates.add(it.y)
                        }
                        intent.action = GET_DATA
                        intent.putExtra(X_COORDINATS, xCoordinates.toDoubleArray())
                        intent.putExtra(Y_COORDINATS, yCoordinates.toDoubleArray())
                        intent.putExtra(COUNTER, request)
                        intent.putExtra(START, starList[request])
                        intent.putExtra(STOP, stopList[request])
                        applicationContext.sendBroadcast(intent)
                        coord2[request].add(0, list)
                        onCommandComplete()
                    } catch (e: Exception) {
                        println(e)
                    }
                }
            } catch (e:Exception) {
                println(e)
            }
        }
    }

    private fun onCommandComplete() {
        if (serialVM != null) {
            //serialVM!!.close()
            request++
            //stopTime = System.currentTimeMillis()
            //println(AnalyzeViewModel.stopTime - AnalyzeViewModel.startTime)
        }
    }

    private fun receiveStreamData(frequency: Long, amplitude: Double) {
        /*if (_signalSeries != null) {
            _signalSeries!!.add((frequency / 1000000L).toDouble(), amplitude)
        }*/
        val coordinates = DataPoint((frequency / 1000000.0), amplitude)
        listCoordinates.add(coordinates)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun convertToBitmap() {
        convertJob = CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            while (check) {
                if (counter == imageCounter) counter = 0
                try {
                    while (coord2[counter].size >= 100) {
                        Log.i("Counter","до удаления" + " " + coord2[counter].size.toString())
                        coord2[counter].removeLast()
                        Log.i("Counter","после удаления" + " " + coord2[counter].size.toString())
                    }
                    if (coord2[counter].isNotEmpty()) {
                        addListsSeries(coord2[counter])
                    }
                }catch (e:Exception){
                    println(e.message)
                }
                delay(100L)
            }
        }
        convertJob!!.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addListsSeries(coord: MutableList<List<DataPoint>>) {
        val width = (((_stop - _start) / 1000000L).toDouble() / (_step.toDouble() / (1000000L).toDouble()))
        val bitmap = Bitmap.createBitmap(width.toInt()+1,100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 30, 30))
        val listSeries = mutableListOf<Bitmap>()
        val list = mutableListOf<List<Bitmap>>()
        for (i in 0 until coord.size) {
            var x = 0
            if (i >= 100) break
            for (j in 0 until coord[i].size) {
                if (j == coord[i].lastIndex) {
                    break
                }
                if (coord[i][j].x == coord[i][j + 1].x)
                    continue
                else {
                    val color = 255 + coord[i][j].y.toInt()
                    val colorForPixel = if (checkSA6) densityColorSA6(color) else densityColor(color)
                    bitmap.setPixel(
                        x,
                        i,
                        Color.rgb( colorForPixel, colorForPixel, colorForPixel)
                    )
                    listSeries.add(bitmap)
                    x++
                }
            }
            list.add(0, listSeries)
        }
        if (coord[0][0].x == (starList[counter] / 1000000L).toDouble()) {
            for (i in list.indices) {
                for (j in list.indices) {
                    imageList[counter].setImageBitmap(list[i][j])
                }
            }
        }
        val _bitmap = imageList[counter].drawable.toBitmap(340, 340, Bitmap.Config.ARGB_8888)
        model?.let { scanImage(mediaPlayer, it, _bitmap, vibration) }
    }

    private fun densityColor(color: Int): Int {
        val k = 255 / 40
        val x = color - 160
        val _color = x * k
        return if (_color<30) 30
        else if (_color >= 255) 255
        else _color
    }

    private fun densityColorSA6(color: Int): Int {
        val k = 255 / 40
        val x = color - 150
        val _color = x * k
        return if (_color<30) 30
        else if (_color >= 255) 255
        else _color
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun scanImage(mediaPlayer: MediaPlayer, model: Model, bitmap: Bitmap, vibration: Vibrator) {
            val image = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            val inputFeature0 =
                TensorBuffer.createFixedSize(intArrayOf(1, 64, 64, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4 * 64 * 64 * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(64 * 64)
            image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)
            var pixel = 0
            for (i in 0 until 64) {
                for (j in 0 until 64) {
                    val `val` = intValues[pixel] // RGB
                    byteBuffer.putFloat(((`val` shr 16 and 0xFF).toFloat()))
                    byteBuffer.putFloat(((`val` shr 8 and 0xFF).toFloat()))
                    byteBuffer.putFloat(((`val` and 0xFF).toFloat()))
                    pixel++
                }
            }

            inputFeature0.loadBuffer(byteBuffer)

// Runs model inference and gets result.
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences: FloatArray = outputFeature0.floatArray
            // find the index of the class with the biggest confidence.
            // find the index of the class with the biggest confidence.
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }
            val classes = arrayOf("Drone", "Non Drone")
            Log.i("Dron", classes[maxPos])
            if (classes[maxPos]=="Drone"){
                if (soundType == 0) {
                    vibration.vibrate(
                        VibrationEffect.createOneShot(
                            100L,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    if (mediaPlayer.isPlaying) println("уже играет")
                    else mediaPlayer.start()
                }
            }
        counter++
    }
}