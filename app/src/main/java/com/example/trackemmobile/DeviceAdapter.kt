package com.example.trackemmobile

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
        val device = deviceList[position]
        holder.bind(device)
    }

    override fun getItemCount() = deviceList.size

    fun updateList(newList: List<DeviceFingerprint>) {
        deviceList = newList
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<DeviceFingerprint> = deviceList

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMakeModel: TextView = itemView.findViewById(R.id.tvMakeModel)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvBssid: TextView = itemView.findViewById(R.id.tvBssid)
        private val tvRequests: TextView = itemView.findViewById(R.id.tvRequests)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)

        fun bind(device: DeviceFingerprint) {
            tvName.text = device.finalDisplayName
            tvMakeModel.text = device.makeModel

            tvBssid.text = "MAC: ${device.mac ?: "N/A"}"
            tvRequests.text = "Requests: ${device.requestCount}"
            tvRssi.text = "RSSI: ${device.lastRssi} dBm"

            // --- FIX: Show the popup menu on a single click ---
            itemView.setOnClickListener {
                showPopupMenu(it, device)
            }
            // --- End of Fix ---

            // We no longer need a separate long-click listener.
            itemView.setOnLongClickListener(null)
        }

        private fun showPopupMenu(view: View, device: DeviceFingerprint) {
            val popup = PopupMenu(view.context, view)
            popup.menu.add("View Details").setOnMenuItemClickListener {
                onDeviceAction(device, "details")
                true
            }
            popup.menu.add("Rename (Set Alias)").setOnMenuItemClickListener {
                onDeviceAction(device, "rename")
                true
            }
            popup.menu.add("Set as Target").setOnMenuItemClickListener {
                onDeviceAction(device, "target")
                true
            }
            popup.menu.add("Ignore Device").setOnMenuItemClickListener {
                onDeviceAction(device, "ignore")
                true
            }
            popup.show()
        }
    }
}
