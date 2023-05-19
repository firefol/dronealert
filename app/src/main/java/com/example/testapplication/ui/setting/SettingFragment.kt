package com.example.testapplication.ui.setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.testapplication.R
import com.example.testapplication.databinding.FragmentSettingBinding
import com.example.testapplication.ui.ViewAdapter
import com.example.testapplication.utils.DroneAlertSettings


class SettingFragment : Fragment() {

    private lateinit var binding: FragmentSettingBinding
    private val viewAdapter: ViewAdapter?
        get() = binding.typeSettingsView.adapter as? ViewAdapter

    lateinit var droneAlertSettings: DroneAlertSettings

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar2?.navigationIcon = resources.getDrawable(R.drawable.baseline_arrow_back_24)
        binding.toolbar2?.setNavigationOnClickListener(View.OnClickListener {
            findNavController().navigate(R.id.mainFragment)
        })
        val viewAdapter = ViewAdapter()
        droneAlertSettings = DroneAlertSettings(requireContext())

        viewAdapter += ViewAdapter.DataItem(
            id = R.id.settingsConectionType,
            name = "Тип подключения",
            data = ViewAdapter.EnumEditData(
                variants = requireContext().resources.getStringArray(R.array.array_connection_types),
                value = droneAlertSettings.connectionType
            )
        )

        viewAdapter += ViewAdapter.DataItem(
            id = R.id.infoVersion,
            name = "Версия приложения",
            data = ViewAdapter.TextEditData(
                value = "Drone alert " + requireContext().packageManager.getPackageInfo(
                    requireContext().packageName,
                    0
                ).versionName
            ),
            isReadOnly = true
        )

        viewAdapter += ViewAdapter.DataItem(
            id = R.id.settingsCounterGraph,
            name = "Количество графиков",
            data = ViewAdapter.EnumEditData(
                variants = requireContext().resources.getStringArray(R.array.counter_graph),
                value = droneAlertSettings.counterGraph
            )
        )

        binding.typeSettingsView.adapter = viewAdapter
        binding.typeSettingsView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
    }

    override fun onPause() {
        super.onPause()
        droneAlertSettings.connectionType = ((viewAdapter?.getItemById(R.id.settingsConectionType)
                as? ViewAdapter.DataItem)?.data as? ViewAdapter.EnumEditData)?.value ?: 0
        droneAlertSettings.counterGraph = ((viewAdapter?.getItemById(R.id.settingsCounterGraph)
                as? ViewAdapter.DataItem)?.data as? ViewAdapter.EnumEditData)?.value ?: 0
        //printerSettings.printerTemplate = viewAdapter?.getEnumValue(R.id.settingsPrinterConectionType) ?: 0
    }
}