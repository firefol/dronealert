package com.example.testapplication.ui.main

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.view.get
import androidx.core.view.indices
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.testapplication.R
import com.example.testapplication.databinding.FragmentMainBinding
import com.example.testapplication.ui.analyze.AnalyzeViewModel
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.jjoe64.graphview.series.DataPoint
import java.util.*

class MainFragment : Fragment() {


    private lateinit var binding: FragmentMainBinding
    private val analyzeViewModel: AnalyzeViewModel by activityViewModels()
    lateinit var usbManager: UsbManager
    var device: UsbDevice? = null
    var serial: UsbSerialDevice? = null
    var connection: UsbDeviceConnection? = null
    private var allEds: MutableList<View>? = null

    val ACTION_USB_PERMISSION = "permission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("WrongConstant", "InflateParams", "SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.inflateMenu(R.menu.toolbar)
        usbManager = context?.getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(
            requireContext(),
            brodcastReciever,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.setting -> {
                    findNavController().navigate(R.id.settingFragment)
                    true
                }
                else -> false
            }
        }
        binding.connectButton.setOnClickListener {
            if (analyzeViewModel.serialVM != null) {
                analyzeViewModel.starList.clear()
                analyzeViewModel.stopList.clear()
                analyzeViewModel.graphCounter = binding.linearlistlayout.size
                analyzeViewModel.coord2.clear()
                for (i in binding.linearlistlayout.indices) {
                    analyzeViewModel.starList.add(binding.linearlistlayout[i].findViewById<EditText>(R.id.editTextNumber).text.toString().toLong() * 1000000L)
                    analyzeViewModel.stopList.add(binding.linearlistlayout[i].findViewById<EditText>(R.id.editTextNumber2).text.toString().toLong() * 1000000L)
                    analyzeViewModel._step = (binding.editTextNumber3.text.toString().toLong() * 1000L)
                    analyzeViewModel.coord2.add(mutableListOf())
                    }
                analyzeViewModel.listCoordinates.clear()
                findNavController().navigate(R.id.analyzeFragment)
            }
        }
        allEds = mutableListOf()
        for (i in 0 until analyzeViewModel.graphCounter) {
            val viewItems = layoutInflater.inflate(R.layout.view_item, null, false)
            allEds!!.add(viewItems)
            binding.linearlistlayout.addView(viewItems)
            val editTextMin = binding.linearlistlayout[i].findViewById<EditText>(R.id.editTextNumber)
            val editTextMax = binding.linearlistlayout[i].findViewById<EditText>(R.id.editTextNumber2)
            if (analyzeViewModel.starList.isEmpty())
                editTextMin.setText("2400")
            else editTextMin.setText((analyzeViewModel.starList[i] / 1000000).toString())
            if (analyzeViewModel.stopList.isEmpty())
                editTextMax.setText("2500")
            else editTextMax.setText((analyzeViewModel.stopList[i] / 1000000).toString())
            clickView(viewItems)
        }
        if (!(usbManager.deviceList.isNotEmpty() && analyzeViewModel.checkConnect)){
            starUsbConnecting()
        }
    }

    @SuppressLint("InflateParams")
    private fun clickView(view: View) {
        val imageButtonAdd = view.findViewById<ImageButton>(R.id.imageButton2)
        val imageButtonDelete = view.findViewById<ImageButton>(R.id.imageButton3)
        imageButtonDelete.setOnClickListener {
            if (allEds!!.size == 1) return@setOnClickListener
            else {
                try {
                    binding.linearlistlayout.removeView(view)
                    allEds!!.remove(view)
                } catch (ex: IndexOutOfBoundsException) {
                    ex.printStackTrace()
                }
            }
        }
        imageButtonAdd.setOnClickListener {
            if (allEds!!.size == analyzeViewModel.maxGraphCounter) return@setOnClickListener
            else {
                try {
                    //binding.linearlistlayout.removeView(view)
                    val view1 = layoutInflater.inflate(R.layout.view_item, null, false)
                    allEds!!.add(view1)
                    binding.linearlistlayout.addView(view1)
                    clickView(view1)
                } catch (ex: IndexOutOfBoundsException) {
                    ex.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun starUsbConnecting() {
        val usbDevices: HashMap<String, UsbDevice>? = usbManager.deviceList
        if (!usbDevices?.isEmpty()!!) {
            var keep = true
            usbDevices.forEach { entry ->
                device = entry.value
                val deviceVendorId = device!!.vendorId
                val flags =  FLAG_MUTABLE
                val context = requireContext()
                //Toast.makeText(requireContext(), deviceVendorId.toString(), Toast.LENGTH_LONG).show()
                val intent = PendingIntent.getBroadcast(
                    requireContext(),
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    flags
                )
                usbManager.requestPermission(device, intent)
                keep = false
            }
            if (!keep) {
                return
            }
        } else {
            println("no usb device connected")
            Toast.makeText(requireContext(), "no usb device connected", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnect() {
        serial?.close()
    }

    private val brodcastReciever = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action!! == ACTION_USB_PERMISSION) {
                val granted: Boolean =
                    intent.extras!!.getBoolean((UsbManager.EXTRA_PERMISSION_GRANTED))
                if (granted) {
                    analyzeViewModel.checkConnect = true
                    connection = usbManager.openDevice(device)
                    serial = UsbSerialDevice.createUsbSerialDevice(device, connection)
                    if (serial != null) {
                        if (serial!!.isOpen) {
                            serial!!.setBaudRate(11520)
                            serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                            serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                            serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                            analyzeViewModel.serialVM = serial
                        } else {
                            //Toast.makeText(context, "port not open", Toast.LENGTH_LONG).show()
                            serial!!.open()
                            serial!!.setBaudRate(11520)
                            serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            serial!!.setParity(UsbSerialInterface.PARITY_ODD)
                            serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                            analyzeViewModel.serialVM = serial

                        }
                    } else {
                        println("port is null")
                    }
                } else {
                    println("permission not granted")
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                starUsbConnecting()
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                disconnect()
            }
        }

    }
}