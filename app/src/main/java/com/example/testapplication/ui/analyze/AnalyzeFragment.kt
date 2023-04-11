package com.example.testapplication.ui.analyze

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
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
        binding.graph1.viewport.setMinX((analyzeViewModel.starList[0] / 1000000L).toDouble())
        binding.graph1.viewport.setMaxX((analyzeViewModel.stopList[0]/ 1000000L).toDouble())
        binding.graph1.viewport.isXAxisBoundsManual = true
        binding.graph1.viewport.setMinY(-100.00)
        binding.graph1.viewport.setMaxY(0.00)
        binding.graph1.viewport.isYAxisBoundsManual = true
        if (analyzeViewModel.starList.size == 1){
            binding.graph2.visibility = GONE
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
        binding.imageView1.viewTreeObserver.addOnWindowFocusChangeListener {  }
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
        val width = binding.imageView1.width
        analyzeViewModel.startScan()
        analyzeViewModel.read()
        //analyzeViewModel.waterfallScan()
        binding.button.setOnClickListener {
            findNavController().navigate(R.id.mainFragment)
        }
    }

    override fun onResume() {
        super.onResume()

    }
}