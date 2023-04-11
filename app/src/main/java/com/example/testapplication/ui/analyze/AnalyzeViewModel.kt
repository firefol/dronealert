package com.example.testapplication.ui.analyze

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testapplication.R
import com.example.testapplication.utils.IStreamReceiver
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback
import com.jjoe64.graphview.series.DataPoint
import kotlinx.coroutines.*


class AnalyzeViewModel : ViewModel() {

    var _start: Long = 0
    var _stop: Long = 0
    var _step: Long = 0
    var starList = mutableListOf<Long>()
    var stopList = mutableListOf<Long>()
    var request = 0
    private var _attenuation: Long = 0
    private var _pointIndex = 0
    private var _pointShift = 0
    private var _lastPointId = 0
    private var _streamReceiver: IStreamReceiver? = null
    private var _messageIndex = 0
    private val _message = ByteArray(2048)
    var check = true


    companion object {
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
    }

    var serialVM: UsbSerialDevice? = null
    private var liveDataCoordinates: MutableLiveData<List<DataPoint>> = MutableLiveData()
    var listCoordinates = mutableListOf<DataPoint>()
    var coord1 = mutableListOf<List<DataPoint>>()
    var coord2 = mutableListOf<List<DataPoint>>()
    private var liveDataSeries1: MutableLiveData<List<List<Bitmap>>> =
        MutableLiveData()
    private var liveDataSeries2: MutableLiveData<List<List<Bitmap>>> =
        MutableLiveData()


    fun getLiveDataObserver(): LiveData<List<DataPoint>> {
        return liveDataCoordinates
    }

    fun getLiveDataSeriesObserver1(): LiveData<List<List<Bitmap>>> {
        return liveDataSeries1
    }

    fun getLiveDataSeriesObserver2(): LiveData<List<List<Bitmap>>> {
        return liveDataSeries2
    }


    fun startScan() {
        val job = viewModelScope.launch(Dispatchers.IO) {
            while (check) {
                if (request == 0) {
                    _start = starList[request]
                    _stop = stopList[request]
                    request = 1
                } else {
                    if (starList.size == 1 && stopList.size == 1)
                    {
                        request = 0
                        _start = starList[request]
                        _stop = stopList[request]
                    } else {
                        _start = starList[request]
                        _stop = stopList[request]
                        request = 0
                    }
                }
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
                delay(200L)
            }
        }
        job.start()
    }

    /*@RequiresApi(Build.VERSION_CODES.O)
    @OptIn(DelicateCoroutinesApi::class)
    fun waterfallScan() {
        val job = viewModelScope.launch(newSingleThreadContext("Custom Thread")) {
            while (check) {
                qwe()
                delay(500L)
            }
        }
        job.start()
    }*/

    @RequiresApi(Build.VERSION_CODES.O)
    private val mCallback = UsbReadCallback {
        val buffer = it
        if (buffer == null) {
            return@UsbReadCallback
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

    fun initStreamReceiver(streamReceiver: IStreamReceiver?) {
        _streamReceiver = streamReceiver
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun read() {
        val buffer = serialVM?.read(mCallback)
        println(buffer)
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
        } else if (readMessage.contentEquals("complete")) {
            val list = listCoordinates.sortedBy { it.x }
            if (list.size == 301 && _step == 500000L) {
                if (list[0].x == (starList[0] / 1000000L).toDouble()){
                    coord1.add(0, list)
                    liveDataCoordinates.postValue(list)
                    qwe(coord1)
                    onCommandComplete()
                } else {
                    coord2.add(0, list)
                    liveDataCoordinates.postValue(list)
                    qwe(coord2)
                    onCommandComplete()
                }
            } else if (list.size == 201 && _step == 1000000L) {
                if (list[0].x == (starList[0] / 1000000L).toDouble()){
                    coord1.add(0, list)
                    liveDataCoordinates.postValue(list)
                    qwe(coord1)
                    onCommandComplete()
                } else {
                    coord2.add(0, list)
                    liveDataCoordinates.postValue(list)
                    qwe(coord2)
                    onCommandComplete()
                }
            }
        }
    }

    private fun receiveStreamData(frequency: Long, amplitude: Double) {
        /*if (_signalSeries != null) {
            _signalSeries!!.add((frequency / 1000000L).toDouble(), amplitude)
        }*/
        val coordinates = DataPoint((frequency / 1000000.0), amplitude)
        listCoordinates.add(coordinates)
    }

    private fun onCommandComplete() {
        if (serialVM != null) {
            //serialVM!!.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun qwe(coord: MutableList<List<DataPoint>>) {
        if (coord.size == 100) {
            coord.removeLast()
        }
        if (coord.isNotEmpty()) {
            addListsSeries(coord)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun addListsSeries(coord: MutableList<List<DataPoint>>) {
        val width = (((_stop - _start) / 1000000L).toDouble() / (_step.toDouble() / (1000000L).toDouble()))
        val bitmap = Bitmap.createBitmap(width.toInt()+1,100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.argb((255 - 99).toFloat(), 0F, 0F, 0F))
        val listSeries = mutableListOf<Bitmap>()
        val list = mutableListOf<List<Bitmap>>()
        for (i in 0 until coord.size) {
            var x = 0
            for (j in 0 until coord[i].size) {
                if (j == coord[i].lastIndex) {
                    break
                }
                if (coord[i][j].x == coord[i][j + 1].x)
                    continue
                else {
                    bitmap.setPixel(
                        x,
                        i,
                        Color.argb(((255 + coord[i][j].y.toInt()).toFloat()), 0F, 0F, 0F)
                    )
                    listSeries.add(bitmap)
                    x++
                }
            }
            list.add(0, listSeries)
        }
        if (coord[0][0].x == (starList[0] / 1000000L).toDouble()) liveDataSeries1.postValue(list)
        else liveDataSeries2.postValue(list)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun processDeviceResponse(message: ByteArray?) {
        val messageLength = message?.size
        if (messageLength == 2 || messageLength == 6) {
            processMessage(message)
        } else {
            val readMessage = message?.let { String(it, 0, message.size) }
            if (readMessage != null) {
                processMessage(readMessage)
            }
        }
    }


    private fun color(avgAmplitude: Double): Int {
        return when (avgAmplitude) {
            in -75.0..0.00 -> R.color.purple_700
            else -> R.color.white
        }
    }
}