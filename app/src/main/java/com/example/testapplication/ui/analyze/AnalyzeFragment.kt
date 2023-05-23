package com.example.testapplication.ui.analyze

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.usb.UsbManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.testapplication.R
import com.example.testapplication.databinding.FragmentAnalyzeBinding
import com.example.testapplication.ml.Model
import com.example.testapplication.service.DroneAlertService
import com.example.testapplication.utils.DroneAlertSettings
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.properties.Delegates


class AnalyzeFragment : Fragment() {

    private lateinit var binding: FragmentAnalyzeBinding
    private val analyzeViewModel: AnalyzeViewModel by activityViewModels()
    var graphCounter = 0
    var imageCounter = 0
    var droneStatusCounter = 0
    private lateinit var mediaPlayer:MediaPlayer


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAnalyzeBinding.inflate(inflater, container, false)
        //analyzeViewModel.initStreamReceiver(this)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ResourceAsColor", "InflateParams", "MissingInflatedId", "CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val setting = DroneAlertSettings(requireContext())
        analyzeViewModel.soundType = setting.connectionType
        binding.linearLayout.post {
            if (analyzeViewModel.graphCounter > 6) {
                binding.linearLayout.visibility = GONE
            } else {
                val height = binding.linearLayout.height
                for (i in 0 until analyzeViewModel.graphCounter) {
                    val graphItems = layoutInflater.inflate(R.layout.graph_item, null, false)
                    binding.linearLayout.addView(graphItems)
                    val graphView = binding.linearLayout[i].findViewById<GraphView>(R.id.graph123)
                    //val metrics = resources.displayMetrics.density
                    //graphView.layoutParams.height = ((450 * metrics) / analyzeViewModel.graphCounter).toInt()
                    graphView.layoutParams.height = height /
                            analyzeViewModel.graphCounter
                    graphView.viewport.setMinX((DroneAlertService.starList[i] / 1000000L).toDouble())
                    graphView.viewport.setMaxX((DroneAlertService.stopList[i] / 1000000L).toDouble())
                    graphView.viewport.isXAxisBoundsManual = true
                    graphView.viewport.setMinY(-100.00)
                    graphView.viewport.setMaxY(-20.00)
                    graphView.viewport.isYAxisBoundsManual = true
                }
                analyzeViewModel.getLiveDataObserver().observe(viewLifecycleOwner) { list ->
                    if (graphCounter == analyzeViewModel.graphCounter) graphCounter = 0
                    try {
                        val graphView = binding.linearLayout[graphCounter].findViewById<GraphView>(R.id.graph123)
                        if (list[0].x == (DroneAlertService.starList[graphCounter] / 1000000L).toDouble()) {
                            //binding.graph1.removeAllSeries()
                            //if (list.size == 301) {
                            graphView.removeAllSeries()
                            val series: LineGraphSeries<DataPoint> = LineGraphSeries(
                                list.toTypedArray()
                            )
                            series.thickness = 3
                            //binding.graph1.addSeries(series)
                            graphView.addSeries(series)
                            graphCounter++
                        } else {
                            graphView.removeAllSeries()
                            //binding.graph2.removeAllSeries()
                            //if (list.size == 301) {
                            val series: LineGraphSeries<DataPoint> = LineGraphSeries(
                                list.toTypedArray()
                            )
                            series.thickness = 3
                            graphView.addSeries(series)
                            //binding.graph2.addSeries(series)
                        }
                        //} else
                        //println("количество${list.size}")
                    } catch (e:Exception) {
                        println(e)
                    }
                }
            }
            analyzeViewModel.check = true
            //analyzeViewModel.startScan()
           // analyzeViewModel.read()
            val filter = IntentFilter()
            filter.addAction(DroneAlertService.GET_DATA)
            registerReceiver(requireContext(),
                brodcastReciever,
                filter,
            RECEIVER_NOT_EXPORTED)
        }
        mediaPlayer = if (analyzeViewModel.soundType == 1) MediaPlayer.create(context, R.raw.alarmbuzzer)
        else MediaPlayer.create(context, R.raw.sirena)
        val model = Model.newInstance(requireContext())
        val vibration = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (analyzeViewModel.graphCounter > 6) {
            binding.linearLayout.visibility = GONE
            binding.gridLayout.rowCount = 4
        } else {
        }
        for (i in 0 until analyzeViewModel.graphCounter) {
            val imageViewItem = layoutInflater.inflate(R.layout.imageview_item, null, false)
            imageViewItem.findViewById<TextView>(R.id.textViewDiapason).text =
                (DroneAlertService.starList[i] / 1000000L).toString() + " - " +
                        (DroneAlertService.stopList[i] / 1000000L).toString()
            binding.gridLayout.addView(imageViewItem)
        }
        /*binding.graph2.viewport.setMinX(2400.00)
        binding.graph2.viewport.setMaxX(2500.00)
        binding.graph2.viewport.isXAxisBoundsManual = true
        binding.graph2.viewport.setMinY(analyzeViewModel.yCount - 100)
        binding.graph2.viewport.setMaxY(analyzeViewModel.yCount)
        binding.graph2.viewport.isYAxisBoundsManual = true
        binding.graph2.gridLabelRenderer.isVerticalLabelsVisible = false
        binding.graph2.gridLabelRenderer.isHorizontalLabelsVisible = false
        binding.graph2.gridLabelRenderer.setHumanRounding(false)*/
        analyzeViewModel.getLiveDataSeriesObserver1().observe(viewLifecycleOwner) {
            if (imageCounter == analyzeViewModel.graphCounter) imageCounter = 0
            val imageViewItem = binding.gridLayout[imageCounter].findViewById<ImageView>(R.id.imageView)
            /*for (i in it){
                binding.graph2.addSeries(i)
            }
            binding.graph2.viewport.setMinY(analyzeViewModel.yCount - 100)
            binding.graph2.viewport.setMaxY(analyzeViewModel.yCount)
            binding.graph2.viewport.isYAxisBoundsManual = true
            analyzeViewModel.yCount++
            if (binding.graph2.series.size > 100)
                binding.graph2.series.removeLast()*/
            for (i in it.indices){
                for (j in it.indices) {
                    imageViewItem.setImageBitmap(it[i][j])

                }
            }
            val bitmap = imageViewItem.drawable.toBitmap(340, 340, Bitmap.Config.ARGB_8888)
            analyzeViewModel.scanImage(mediaPlayer, model, bitmap, vibration)
            imageCounter++
        }
        analyzeViewModel.getLiveDataDroneStatus().observe(viewLifecycleOwner) {
            if (droneStatusCounter == analyzeViewModel.graphCounter) droneStatusCounter = 0
            val imageViewItem = binding.gridLayout[droneStatusCounter].findViewById<ImageView>(R.id.imageView)
            if (it == "Drone") imageViewItem.setBackgroundResource(R.drawable.image_background)
            else imageViewItem.setBackgroundResource(0)
            droneStatusCounter++
        }
        binding.screenButton.setOnClickListener {
            val imageViewItem = binding.gridLayout[0].findViewById<ImageView>(R.id.imageView)
            val bitmap = getScreenShotFromView(imageViewItem)
            if (bitmap != null) {
                saveMediaToStorage(bitmap)
            }
            /*val boolean = checkPermission()
            if (!boolean) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE),
                    1
                )
            }
            else {
                val bitmap = getScreenShotFromView(binding.linearLayout2)
                if (bitmap != null) {
                    saveMediaToStorage(bitmap)
                }

            }*/
        }
        binding.RecordButton.setOnClickListener {
            if (!analyzeViewModel.recordCheck) {
                binding.RecordButton.text = "Стоп"
                if (SDK_INT >= 30) {
                    if (!Environment.isExternalStorageManager()) {
                        val getpermission = Intent()
                        getpermission.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        startActivity(getpermission)
                    }
                }
                analyzeViewModel.recordCheck = true
            } else if (analyzeViewModel.recordCheck){
                analyzeViewModel.recordCheck = false
                binding.RecordButton.text = "Запись"
                val inflater = layoutInflater
                val dialogLayout = inflater.inflate(R.layout.alert_dialog,null)
                val text = dialogLayout.findViewById<EditText>(R.id.editTextText)
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("Дрон или не дрон!")
                    .setPositiveButton("Сохранить") {
                            dialog, id ->
                        analyzeViewModel.recordToFile(text.text.toString())
                    }
                builder.setView(dialogLayout)
                builder.show()
            }
        }
        binding.backButton.setOnClickListener {
            analyzeViewModel.check = false
            analyzeViewModel.request = 0
            graphCounter = 0
            imageCounter = 0
            droneStatusCounter = 0
            findNavController().navigate(R.id.mainFragment)
        }
    }

    private fun checkPermission(): Boolean {
        return if (SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val result =
                ContextCompat.checkSelfPermission(requireContext(), READ_EXTERNAL_STORAGE)
            val result1 =
                ContextCompat.checkSelfPermission(requireContext(), WRITE_EXTERNAL_STORAGE)
            result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getScreenShotFromView(v: View): Bitmap? {
        // create a bitmap object
        var screenshot: Bitmap? = null
        try {
            screenshot = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            // Now draw this bitmap on a canvas
            val canvas = Canvas(screenshot)
            v.draw(canvas)
        } catch (e: Exception) {
            Log.e("GFG", "Failed to capture screenshot because:" + e.message)
        }
        // return the bitmap
        return screenshot
    }

    private fun saveMediaToStorage(bitmap: Bitmap) {
        // Generating a file name
        val filename = "${System.currentTimeMillis()}.png"

        // Output stream
        var fos: OutputStream? = null

        // For devices running android >= Q
        if (SDK_INT >= Build.VERSION_CODES.Q) {
            // getting the contentResolver
            requireContext().contentResolver?.also { resolver ->

                // Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    // putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                // Inserting the contentValues to
                // contentResolver and getting the Uri
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                // Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            // These for devices running on android < Q
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            // Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(requireContext() , "Captured View and saved to Gallery" , Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        graphCounter = 0
        imageCounter = 0
        droneStatusCounter = 0
        requireContext().unregisterReceiver(brodcastReciever)
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        println("стоп")

    }

    private val brodcastReciever = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context?, intent: Intent?) {
            val xList = intent!!.getDoubleArrayExtra(DroneAlertService.X_COORDINATS)!!.toList()
            val yList = intent.getDoubleArrayExtra(DroneAlertService.Y_COORDINATS)!!.toList()
            val counter = intent.getIntExtra(DroneAlertService.COUNTER, 0)
            analyzeViewModel._start = intent.getLongExtra(DroneAlertService.START,0L)
            analyzeViewModel._stop = intent.getLongExtra(DroneAlertService.STOP, 0L)
            analyzeViewModel.convertToGraphPoint(xList, yList, counter)
        }
    }
}