package com.example.trackemmobile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onDeviceAction: (DeviceFingerprint, String) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private var deviceList = listOf<DeviceFingerprint>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(deviceList[position])
    }

    override fun getItemCount() = deviceList.size

    fun updateList(newList: List<DeviceFingerprint>) {
        deviceList = newList
        notifyDataSetChanged()
    }

    fun getCurrentList() = deviceList

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvMakeModel: TextView = itemView.findViewById(R.id.tvMakeModel)
        private val tvBssid: TextView = itemView.findViewById(R.id.tvBssid)
        private val tvRequests: TextView = itemView.findViewById(R.id.tvRequests)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)

        fun bind(device: DeviceFingerprint) {
            if (device.isRandomizedHost) {
                tvName.text = device.finalDisplayName
                tvName.setTextColor(Color.YELLOW)
                tvMakeModel.text = "Randomized Host • ${device.allMacs.size} MACs"
                tvBssid.text = "Current: ${device.mac}"
                itemView.setBackgroundColor(Color.parseColor("#33FF5722"))
            } else {
                tvName.text = device.finalDisplayName
                tvMakeModel.text = device.makeModel
                tvBssid.text = "MAC: ${device.mac ?: "N/A"}"
                itemView.setBackgroundColor(Color.TRANSPARENT)
            }
            tvRequests.text = "Seen: ${device.requestCount}"
            tvRssi.text = "RSSI: ${device.lastRssi} dBm"

            itemView.setOnClickListener { showPopupMenu(it, device) }
        }

        private fun showPopupMenu(view: View, device: DeviceFingerprint) {
            PopupMenu(view.context, view).apply {
                menu.add("View Details").setOnMenuItemClickListener {
                    onDeviceAction(device, "details"); true
                }
                menu.add("Rename").setOnMenuItemClickListener {
                    onDeviceAction(device, "rename"); true
                }
                menu.add("Set as Target").setOnMenuItemClickListener {
                    onDeviceAction(device, "target"); true
                }
                menu.add("Ignore").setOnMenuItemClickListener {
                    onDeviceAction(device, "ignore"); true
                }
                show()
            }
        }
    }
}