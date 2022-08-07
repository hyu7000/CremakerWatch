package com.example.CremakerWatch

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.CremakerWatch.R
import kotlinx.android.synthetic.main.notification_list.*

var isThereDataList = false
var doChangeAllValueToFalse = false

class NotificationAppList: AppCompatActivity() {

    companion object{
        lateinit var instanceNotiList: NotificationAppList
    }

    init {
        instanceNotiList = this
    }

    lateinit var sendToAlarmList: SharedPreferences

    lateinit var appRecyclerViewAdapter: AppRecyclerViewAdapter
    val datas = mutableListOf<RecyclerViewItem>()

    var strToCheck:String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notification_list)

        updateRecycler()

        val backButton = findViewById<Button>(R.id.backBtn)
        backButton.setOnClickListener {
            finish()
        }

        val searchBox = findViewById<EditText>(R.id.searchEditText)
        searchBox.doAfterTextChanged{
            strToCheck = searchBox.text.toString()
            isThereDataList = false
            updateRecycler()
            Log.d("cw_test_Text","텍스트 변경됨 "+strToCheck )
        }

        val disableBtn = findViewById<Button>(R.id.allDisableBtn)
        disableBtn.setOnClickListener{
            isThereDataList = false
            doChangeAllValueToFalse = true
            updateRecycler()
            Log.d("cw_test", "모두 변경시도")
        }
    }

    private fun updateRecycler() {
        if(isThereDataList) return

        appRecyclerViewAdapter  = AppRecyclerViewAdapter(this)
        appRecyclerView.adapter = appRecyclerViewAdapter

        datas.clear()

        sendToAlarmList = getSharedPreferences("appListToSendMsg", MODE_PRIVATE)

        val packageManager = this.packageManager
        val applications: List<ApplicationInfo> = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val notificationListLay = LayoutInflater.from(this).inflate(R.layout.notification_list, null)

        for (info: ApplicationInfo in applications) {
            if(doChangeAllValueToFalse){
                sendToAlarmList.edit().putBoolean(info.packageName.toString(), false).apply()
            }

            if(info.packageName.toString().contains(strToCheck) || info.loadLabel(packageManager).toString().contains(strToCheck)){
                datas.add(RecyclerViewItem(info.loadIcon(packageManager), info.loadLabel(packageManager).toString(), info.packageName.toString()))
            }
        }

        val adapter = AppRecyclerViewAdapter(this)
        var notiList = notificationListLay.findViewById<RecyclerView>(R.id.appRecyclerView)
        notiList.adapter = adapter

        datas.apply {
            appRecyclerViewAdapter.datas = datas
            appRecyclerViewAdapter.notifyDataSetChanged()
        }

        appRecyclerView.setLayoutManager(LinearLayoutManager(this));

        isThereDataList = false
        doChangeAllValueToFalse = false
    }

    fun switchValueOfToggleBtn(string:String){
        var isSet = sendToAlarmList.getBoolean(string,false)
        sendToAlarmList.edit().putBoolean(string, !isSet).apply()
        Log.d("cw_test","isSet:"+sendToAlarmList.getBoolean(string,false).toString() + " Str:"+string)
    }

    fun checkValueOfAppList(string: String): Boolean{
        return sendToAlarmList.getBoolean(string,false)
    }
}