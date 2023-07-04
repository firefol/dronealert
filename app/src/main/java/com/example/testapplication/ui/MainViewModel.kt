package com.example.testapplication.ui

import androidx.lifecycle.ViewModel
import com.jjoe64.graphview.series.DataPoint

class MainViewModel: ViewModel() {

    var _start: Long = 0
    var _stop: Long = 0
    var _step: Long = 0
    var starList = mutableListOf<Long>()
    var stopList = mutableListOf<Long>()
    var request = 0
    var graphCounter = 1
    var maxGraphCounter = 5
    var checkConnect = false
    var deviceCount = 0
    var delay = 150L
    var check = true
    var recordCheck = false
    var soundType = 0
    var position:Int? = null
    var coord2 = mutableListOf<MutableList<List<DataPoint>>>()
    var listCoordinates = mutableListOf<DataPoint>()
}