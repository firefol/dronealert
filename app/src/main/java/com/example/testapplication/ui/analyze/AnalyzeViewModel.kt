package com.example.testapplication.ui.analyze

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testapplication.ml.Model
import com.example.testapplication.service.DroneAlertService
import com.jjoe64.graphview.series.DataPoint
import kotlinx.coroutines.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


class AnalyzeViewModel : ViewModel() {

    var _start: Long = 0
    var _stop: Long = 0
    var request = 0
    var graphCounter = 1
    var check = true
    var recordCheck = false
    var soundType = 0
    var i = 0
    private var liveDataCoordinates: MutableLiveData<List<DataPoint>> = MutableLiveData()
    var test = mutableListOf<MutableLiveData<List<DataPoint>>>()
    var coord2 = mutableListOf<MutableList<List<DataPoint>>>()
    var recordList = mutableListOf<List<DataPoint>>()
    private var liveDataSeries1: MutableLiveData<List<List<Bitmap>>> = MutableLiveData()
    private var liveDataDroneStatus: MutableLiveData<String> = MutableLiveData()

    fun getLiveDataObserver(): LiveData<List<DataPoint>> {
        return liveDataCoordinates
    }

    fun getLiveDataSeriesObserver1(): LiveData<List<List<Bitmap>>> {
        return liveDataSeries1
    }

    fun getLiveDataDroneStatus(): LiveData<String> {
        return liveDataDroneStatus
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun convertToBitmap(coord: MutableList<List<DataPoint>>) {
        while(coord.size >= 100) {
            coord2[request].removeLast()
        }
        if (coord.isNotEmpty()) {
            addListsSeries(coord)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun addListsSeries(coord: MutableList<List<DataPoint>>) {
        val width = (((_stop - _start) / 1000000L).toDouble() /
                (DroneAlertService._step.toDouble() / (1000000L).toDouble()))
        val bitmap = Bitmap.createBitmap(width.toInt()+1,100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 30, 30))
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
                    val color = 255 + coord[i][j].y.toInt()
                    val colorForPixel = if (DroneAlertService.checkSA6) densityColorSA6(color) else densityColor(color)
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
        liveDataSeries1.postValue(list)
    }

   fun recordToFile(text:String) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(path.toString() + "/" + text +
                    "${System.currentTimeMillis()}.txt")
            file.createNewFile()
            val fos = FileOutputStream(file).bufferedWriter()
            for (i in 0 until recordList.size) {
                for (j in 0 until recordList[i].size) {
                    if (j == recordList[i].lastIndex) {
                        break
                    }
                    if (recordList[i][j].x == recordList[i][j + 1].x)
                        continue
                    else {
                        fos.write(recordList[i][j].y.toString() + ";")
                    }
                }
                fos.newLine()
            }
            fos.close()
            recordList.clear()
        }
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
    fun scanImage(mediaPlayer: MediaPlayer, model: Model, bitmap: Bitmap, vibration:Vibrator) {
        viewModelScope.launch(Dispatchers.Default) {
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
            liveDataDroneStatus.postValue(classes[maxPos])
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun convertToGraphPoint(xList: List<Double>, yList: List<Double>, counter:Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val listcoordinates = mutableListOf<DataPoint>()
            for (i in xList.indices) {
                val coordinates = DataPoint(xList[i], yList[i])
                listcoordinates.add(coordinates)
            }
            val list = listcoordinates
            coord2[counter].add(0, list)
            liveDataCoordinates.postValue(listcoordinates)
            val coordinates = coord2[counter]
            request = counter
            convertToBitmap(coordinates)
        }
    }
}