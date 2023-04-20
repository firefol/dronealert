package com.example.testapplication.ui.analyze

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.testapplication.R
import com.example.testapplication.databinding.FragmentAnalyzeBinding
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


class AnalyzeFragment : Fragment() {

    private lateinit var binding: FragmentAnalyzeBinding
    private val analyzeViewModel: AnalyzeViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAnalyzeBinding.inflate(inflater, container, false)
        //analyzeViewModel.initStreamReceiver(this)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ResourceAsColor")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyzeViewModel.check = true
        binding.graph1.viewport.setMinX((analyzeViewModel.starList[0] / 1000000L).toDouble())
        binding.graph1.viewport.setMaxX((analyzeViewModel.stopList[0]/ 1000000L).toDouble())
        binding.graph1.viewport.isXAxisBoundsManual = true
        binding.graph1.viewport.setMinY(-100.00)
        binding.graph1.viewport.setMaxY(0.00)
        binding.graph1.viewport.isYAxisBoundsManual = true
        if (analyzeViewModel.starList.size == 1){
            binding.graph2.visibility = GONE
            binding.imageView2.visibility = GONE
        } else {
            binding.graph2.viewport.setMinX((analyzeViewModel.starList[1] / 1000000L).toDouble())
            binding.graph2.viewport.setMaxX((analyzeViewModel.stopList[1] / 1000000L).toDouble())
            binding.graph2.viewport.isXAxisBoundsManual = true
            binding.graph2.viewport.setMinY(-100.00)
            binding.graph2.viewport.setMaxY(0.00)
            binding.graph2.viewport.isYAxisBoundsManual = true
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
        analyzeViewModel.getLiveDataObserver().observe(viewLifecycleOwner) { list ->

             if (list[0].x == (analyzeViewModel.starList[0] / 1000000L).toDouble() ) {
                 binding.graph1.removeAllSeries()
                 //if (list.size == 301) {
                 val series: LineGraphSeries<DataPoint> = LineGraphSeries(
                     list.toTypedArray()
                 )
                 series.thickness = 3
                 binding.graph1.addSeries(series)
             } else {
                 binding.graph2.removeAllSeries()
                 //if (list.size == 301) {
                 val series: LineGraphSeries<DataPoint> = LineGraphSeries(
                     list.toTypedArray()
                 )
                 series.thickness = 3
                 binding.graph2.addSeries(series)
             }
            //} else
            //println("количество${list.size}")
        }
        analyzeViewModel.getLiveDataSeriesObserver1().observe(viewLifecycleOwner) {
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
                    binding.imageView1.setImageBitmap(it[i][j])

                }
            }
        }
        analyzeViewModel.getLiveDataSeriesObserver2().observe(viewLifecycleOwner) {
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
                    binding.imageView2.setImageBitmap(it[i][j])
                }
            }
        }
        analyzeViewModel.startScan()
        analyzeViewModel.read()
        binding.screenButton.setOnClickListener {
            val bitmap = getScreenShotFromView(binding.imageView1)
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

    override fun onDestroy() {
        super.onDestroy()
        analyzeViewModel.check = false
    }
}