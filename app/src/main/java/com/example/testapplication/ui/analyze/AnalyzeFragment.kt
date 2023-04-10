package com.example.testapplication.ui.analyze

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.testapplication.R
import com.example.testapplication.databinding.FragmentAnalyzeBinding
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime


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
        println(binding.linearLayout.layoutParams.height)
        println(binding.graph1.layoutParams.height)
        binding.graph1.viewport.setMinX(2400.00)
        binding.graph1.viewport.setMaxX(2500.00)
        binding.graph1.viewport.isXAxisBoundsManual = true
        binding.graph1.viewport.setMinY(-100.00)
        binding.graph1.viewport.setMaxY(0.00)
        binding.graph1.viewport.isYAxisBoundsManual = true
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
             binding.graph1.removeAllSeries()
             //if (list.size == 301) {
                 val series: LineGraphSeries<DataPoint> = LineGraphSeries(
                     list.toTypedArray()
                 )
                 series.thickness = 3
                 binding.graph1.addSeries(series)
            //} else
            //println("количество${list.size}")
        }
        analyzeViewModel.getLiveDataSeriesObserver().observe(viewLifecycleOwner) {
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
                    binding.imageView.setImageBitmap(it[i][j])
                }
            }
        }
        analyzeViewModel.startScan()
        analyzeViewModel.read()
        //analyzeViewModel.waterfallScan()
        /*val series1: PointsGraphSeries<DataPoint> = PointsGraphSeries(
            arrayOf(DataPoint(2400.0, 0.00),
                    DataPoint(2401.0, 0.00),
                    DataPoint(2402.0, 0.00),
                    DataPoint(2403.0, 0.00),
                    DataPoint(2404.0, 0.00))
        )
        series1.shape = PointsGraphSeries.Shape.RECTANGLE
        series1.size = 5F
        series1.color = Color.argb(Random.nextInt(0,255),0,0,0)
        binding.graph2!!.addSeries(series1)*/
        /*val series2: PointsGraphSeries<DataPoint> = PointsGraphSeries(
            arrayOf(DataPoint(2400.0, 0.00),
                DataPoint(2401.0, 1.00),
                DataPoint(2402.0, 1.00),
                DataPoint(2403.0, 1.00),
                DataPoint(2404.0, 1.00),
                DataPoint(2405.0, 1.00),
                DataPoint(2406.0, 1.00),
                DataPoint(2407.0, 1.00),
                DataPoint(2408.0, 1.00),
                DataPoint(2409.0, 1.00),
                DataPoint(2410.0, 1.00))
        )
        series2.shape = PointsGraphSeries.Shape.RECTANGLE
        series2.size = 5F
        series2.color = Color.argb(Random.nextInt(0,255),0,0,0)
        val series3: PointsGraphSeries<DataPoint> = PointsGraphSeries(
            arrayOf(DataPoint(2400.0, 0.00),
                DataPoint(2401.0, 2.00),
                DataPoint(2402.0, 2.00),
                DataPoint(2403.0, 2.00),
                DataPoint(2404.0, 2.00),
                DataPoint(2405.0, 2.00),
                DataPoint(2406.0, 2.00),
                DataPoint(2407.0, 2.00),
                DataPoint(2408.0, 2.00),
                DataPoint(2409.0, 2.00),
                DataPoint(2410.0, 2.00))
        )
        series3.shape = PointsGraphSeries.Shape.RECTANGLE
        series3.size = 5F
        series3.color = Color.argb(Random.nextInt(0,255),0,0,0)
        val series4: PointsGraphSeries<DataPoint> = PointsGraphSeries(
            arrayOf(DataPoint(2400.0, 0.00),
                DataPoint(2401.0, 3.00),
                DataPoint(2402.0, 3.00),
                DataPoint(2403.0, 3.00),
                DataPoint(2404.0, 3.00),
                DataPoint(2405.0, 3.00),
                DataPoint(2406.0, 3.00),
                DataPoint(2407.0, 3.00),
                DataPoint(2408.0, 3.00),
                DataPoint(2409.0, 3.00),
                DataPoint(2410.0, 3.00))
        )
        series4.shape = PointsGraphSeries.Shape.RECTANGLE
        series4.size = 5F
        series4.color = Color.argb(Random.nextInt(0,255),0,0,0)
        val series5: PointsGraphSeries<DataPoint> = PointsGraphSeries(
            arrayOf(DataPoint(2400.0, 4.00),
                DataPoint(2401.0, 4.00),
                DataPoint(2402.0, 4.00),
                DataPoint(2403.0, 4.00),
                DataPoint(2404.0, 4.00),
                DataPoint(2405.0, 4.00),
                DataPoint(2406.0, 4.00),
                DataPoint(2407.0, 4.00),
                DataPoint(2408.0, 4.00),
                DataPoint(2409.0, 4.00),
                DataPoint(2410.0, 4.00))
        )
        series5.shape = PointsGraphSeries.Shape.RECTANGLE
        series5.size = 5F
        series5.color = Color.argb(Random.nextInt(0,255),0,0,0)
        binding.graph2!!.addSeries(series1)
        binding.graph2!!.addSeries(series2)
        binding.graph2!!.addSeries(series3)
        binding.graph2!!.addSeries(series4)
        binding.graph2!!.addSeries(series5)*/
        /*val series2 = LineGraphSeries(
            arrayOf(
                DataPoint(2400.0, -95.4),
                DataPoint(2401.0, -95.4),
                DataPoint(2402.0, -95.2),
                DataPoint(2403.0, -95.2),
                DataPoint(2404.0, -90.3),
                DataPoint(2405.0, -90.0),
                DataPoint(2406.0, -90.0),
                DataPoint(2407.0, -89.5),
                DataPoint(2408.0, -89.5),
                DataPoint(2409.0, -94.6),
                DataPoint(2410.0, -94.3)
            )
        )
        binding.graph1.addSeries(series2)*/
        /*val series = LineGraphSeries(
            list.toTypedArray()
        )
        binding.graph1.addSeries(series)*/
        binding.button.setOnClickListener {
            findNavController().navigate(R.id.mainFragment)
        }

    }
}