package com.example.CremakerWatch

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ToggleButton
import androidx.recyclerview.widget.RecyclerView
import com.example.CremakerWatch.R

class AppRecyclerViewAdapter(private val context: Context) : RecyclerView.Adapter<AppRecyclerViewAdapter.ViewHolder>() {

    var datas = mutableListOf<RecyclerViewItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.list_item,parent,false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = datas.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(datas[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val toggleBtn: ToggleButton = itemView.findViewById(R.id.toggleBtnToSendWatch)
        private val txtTitle: TextView      = itemView.findViewById(R.id.text_title)
        private val txtSubTitle: TextView   = itemView.findViewById(R.id.text_sub_title)
        private val imgProfile: ImageView   = itemView.findViewById(R.id.image_title)

        fun bind(item: RecyclerViewItem) {
            txtTitle.text = item.title
            txtSubTitle.text = item.subTitle
            imgProfile.setImageDrawable(item.icon)

            toggleBtn.isChecked = false

            var checkToggleValue = NotificationAppList.instanceNotiList.checkValueOfAppList(txtSubTitle.text.toString())

            if(checkToggleValue) {
                toggleBtn.isChecked = checkToggleValue
                Log.d("cw_test","t:"+ txtSubTitle.text.toString())
            }

            toggleBtn.setOnClickListener(){
                NotificationAppList.instanceNotiList.switchValueOfToggleBtn(txtSubTitle.text.toString())
            }
        }

    }
}