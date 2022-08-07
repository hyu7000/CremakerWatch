package com.example.CremakerWatch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call.Details.hasProperty
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.CremakerWatch.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.notification_list.*
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object{
        lateinit var instance: MainActivity
    }

    var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    var REQUEST_ENABLE_BT  = 1
    var scaned_Bt_Count    = 0
    var btListItem         = arrayOfNulls<String>(20)
    private var devicesArr = ArrayList<BluetoothDevice>()

    var recLabelTextView: TextView?         = null
    var recMsgTextView: TextView?           = null
    var textBLS: TextView?                  = null

    var UUID_UART_SERVICE: UUID?            = null
    var UUID_WRITE: UUID?                   = null
    var UUID_NOTIFY: UUID?                  = null
    var CLIENT_CHARACTERISTIC_CONFIG: UUID? = null

    val ACTION_GATT_CONNECTED           = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
    val ACTION_GATT_DISCONNECTED        = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
    val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
    val ACTION_DATA_AVAILABLE           = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"
    val EXTRA_DATA                      = "com.example.bluetooth.le.EXTRA_DATA"

    var getWeather = GetWeatherInfo()

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 글로벌 변수에 현재 경도 위도 값을 얻고 저장함
        instance = this
        var getLocationValue = GetLocation()
        getLocationValue?.getLocation()

        // 날씨 API에 데이터 요청
        Handler(Looper.getMainLooper()).postDelayed({
            getWeather.calXYFromGPS(latitudeValue, longitudeValue)
            getWeather.askAPIWeather()
        }, 10000)

        // 각 컴포넌트 초기화, 초기설정
        textBLS          = findViewById(R.id.bluetooth_State) as TextView
        recLabelTextView = findViewById(R.id.rec_Label_Textview) as TextView
        recMsgTextView   = findViewById(R.id.rec_Msg_Textview) as TextView

        recLabelTextView!!.visibility = View.INVISIBLE
        recMsgTextView!!.visibility   = View.INVISIBLE

        //기기의 BLE 기능을 지원 여부 확인
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d("cw_test","BLE 안됨")
        }

        //블루투스 지원된다면, 각종 권한 요청하기
        if (bluetoothAdapter == null) {
            Log.d("bluetoothAdapter","기기는 블루투스를 지원하지 않음")
        }else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION),98)
                Log.d("cw_test","권한 요청")
            }
        }

        //현재 앱의 알림 허용을 위한 요청 팝업
        val mDialogView = LayoutInflater.from(this).inflate(R.layout.noti_allow_dialog, null)
        val mBuilder = AlertDialog.Builder(this)
            .setView(mDialogView)
            .setTitle("알림 허용")

        var mAlertDialog : AlertDialog? = null

        if (!isNotificationPermissionAllowed()) {
            mAlertDialog = mBuilder.show()
        }

        //
        //버튼 설정
        //

        val notiListBtn: Button = findViewById(R.id.notification_List_Btn)
        notiListBtn.setOnClickListener {
            val intent = Intent(this, NotificationAppList::class.java)
            startActivity(intent)
        }

        val okButton = mDialogView.findViewById<Button>(R.id.successButton)
        okButton.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            Log.d("cw_test","알림 권한 팝업창")
        }

        val noButton = mDialogView.findViewById<Button>(R.id.closeButton)
        noButton.setOnClickListener {
            mAlertDialog?.dismiss()
        }

        //BLE 스캔 시작 버튼의 동작 구성, leScanCallback 함수를 콜백
        var scanBLEBtn: Button = findViewById(R.id.BLE_Scan_Btn)
        scanBLEBtn.setOnClickListener {
            Handler(Looper.getMainLooper()).postDelayed({
                bluetoothAdapter?.stopLeScan(leScanCallback)
                Log.d("cw_test", "핸들러 실행됨")
            }, 20000)

            bluetoothAdapter?.startLeScan(leScanCallback)
            textBLS!!.text = "블루투스 BLE 찾는 중"
        }

        // 연결해제 버튼의 동작 설정
        var disconnectBtn: Button = findViewById(R.id.disconnect_BLE)
        disconnectBtn.setOnClickListener {
            if (bluetoothGatt != null) {
                bluetoothGatt!!.disconnect()
                bluetoothGatt!!.close()
                textBLS!!.text = "연결 해제됨"
            }
        }

        // 리스트뷰 선택시 블루투스 연결 및 스캔 중지
        val list_scaned_Bt: ListView = findViewById(R.id.scanedBt_ListView) as ListView
        list_scaned_Bt.adapter = ArrayAdapter(this,android.R.layout.simple_list_item_1, btListItem)
        list_scaned_Bt.setOnItemClickListener { parent, view, position, id ->
            if(btListItem[position]==null) return@setOnItemClickListener
            if(!isConnected) {
                Toast.makeText(this@MainActivity,parent.getItemAtPosition(position).toString() + "에 연결을 시도합니다.", Toast.LENGTH_SHORT).show()
                list_scaned_Bt.adapter =
                    ArrayAdapter(this, android.R.layout.simple_list_item_1, btListItem)

                bluetoothGatt = devicesArr[position]?.connectGatt(this, false, gattCallback)
                bluetoothDevice = devicesArr[position]
                Log.d("cw_test", bluetoothDevice!!.name.toString())
                textBLS!!.text = "연결 시도 중 : " + bluetoothDevice?.name.toString()
            }
            else{
                Toast.makeText(this@MainActivity,
                    bluetoothDevice?.name.toString()+"연결되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 메세지 보내기 버튼 동작 구성
        var sendMsgBtn: Button = findViewById(R.id.sendMsg_Btn)
        var sendMsgBox: TextInputEditText = findViewById(R.id.msgEditTextBox)
        sendMsgBtn.setOnClickListener {
            if(sendMsgToBLEDevice(sendMsgBox.text.toString())){
                Log.d("cw_test","Send Successfully")
                sendMsgBox.text = null
            }
        }
    }



    @SuppressLint("MissingPermission")
    fun sendMsgToBLEDevice(string: String) :Boolean {
        if(!isConnected) {
            Log.d("cw_test","BLE 연결 상태가 아님")
            return false
        }

        if(string != null && string != "null"){
            curWriteCharacteristic?.setValue(string)
            var result :Boolean = bluetoothGatt?.writeCharacteristic(curWriteCharacteristic)!!
            Log.d("cw_test","send msg result : "+result)
            return true
        }
        
        return false
    }

    @SuppressLint("MissingPermission")
    fun sendMsgToBLEDevice(byteArray: ByteArray) :Boolean {
        if(!isConnected) {
            Log.d("cw_test","BLE 연결 상태가 아님")
            return false
        }

        if(byteArray != null && byteArray.size != 0){
            curWriteCharacteristic?.setValue(byteArray)
            var result :Boolean = bluetoothGatt?.writeCharacteristic(curWriteCharacteristic)!!
            Log.d("cw_test","send msg result : "+result)
            return true
        }

        return false
    }

    private fun isNotificationPermissionAllowed(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(applicationContext)
            .any { enabledPackageName ->
                enabledPackageName == packageName
            }
    }

    @SuppressLint("MissingPermission")
    private val leScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        if (!devicesArr.contains(device) && device.name!=null) {
            devicesArr.add(device)

            btListItem[scaned_Bt_Count] = device?.name.toString()
            scaned_Bt_Count++
            if(scaned_Bt_Count>20) scaned_Bt_Count = 0

            val list_scaned_Bt: ListView = findViewById(R.id.scanedBt_ListView) as ListView
            list_scaned_Bt.adapter = ArrayAdapter(this,android.R.layout.simple_list_item_1, btListItem)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            Log.d("cw_test","Change실행됨")
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    intentAction = ACTION_GATT_CONNECTED
                    broadcastUpdate(intentAction)
                    isConnected = bluetoothGatt?.discoverServices()!!
                    Log.d("cw_test","discoverServices() 실행 됨" + isConnected.toString())
                    if(isConnected)
                        bluetoothAdapter?.stopLeScan(leScanCallback)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    textBLS!!.text = "연결 끊김"
                    intentAction = ACTION_GATT_DISCONNECTED
                    broadcastUpdate(intentAction)
                    isConnected = false
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            var hasWrite = false
            var hasNotify = false
            var hasDescriptor = false
            when (status) {
                BluetoothGatt.GATT_SUCCESS ->
                {
                    var services: List<BluetoothGattService> = gatt.getServices()
                    for (service: BluetoothGattService in services) {
                        for (characteristic: BluetoothGattCharacteristic in service.getCharacteristics())
                        {
                            if( hasProperty(characteristic.properties, BluetoothGattCharacteristic.PROPERTY_WRITE)){
                                gatt.readCharacteristic(characteristic)
                                UUID_WRITE = characteristic.uuid
                                hasWrite = true
                                Log.d("cw_test","readC : "+characteristic.toString())
                                curWriteCharacteristic = characteristic
                            }

                            if( hasProperty(characteristic.properties, BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
                                gatt.setCharacteristicNotification(characteristic, true)
                                UUID_NOTIFY = characteristic.uuid
                                hasNotify = true
                                Log.d("cw_test","readN : "+characteristic.toString())
                                curNotifyCharacteristic = characteristic
                            }

                            if(!hasDescriptor) {
                                for (descriptor in characteristic.descriptors) {
                                    CLIENT_CHARACTERISTIC_CONFIG = descriptor.uuid
                                    Log.d(
                                        "cw_test",
                                        "BluetoothGattDescriptor: " + descriptor.uuid.toString()
                                    )
                                    hasDescriptor = true
                                }
                            }

                            Log.d("cw_test",characteristic.uuid.toString())
                        }

                        if(hasWrite && hasNotify && hasDescriptor) {
                            UUID_UART_SERVICE = service.uuid
                            Log.d("cw_test","ser UUID : "+UUID_UART_SERVICE.toString())
                            val descriptor = curNotifyCharacteristic?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                ?.apply {
                                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                }
                            var testDes = gatt.writeDescriptor(descriptor)
                            Log.d("cw_test","descriptor : "+ descriptor.toString())
                            Log.d("cw_test", "testDes : $testDes")
                            textBLS!!.text = "연결 됨"
                        }
                    }
                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                }
            }
        }

        // Result of a characteristic read operation
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                    bluetoothGatt?.setCharacteristicNotification(characteristic, true)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            recLabelTextView!!.visibility = View.VISIBLE
            recMsgTextView!!.visibility = View.VISIBLE
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }

    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)
        when (characteristic.uuid) {
            else -> {
                val data: ByteArray? = characteristic.value
                val msg = characteristic.getIntValue(FORMAT_UINT8,0)
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                    recMsgTextView!!.text = hexString.toString()
                }
            }
        }
        sendBroadcast(intent)
    }

    //블루투스 권한이 통과되면 블루투스를 활성화함
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            98-> {
                    if(grantResults.get(0) == PackageManager.PERMISSION_GRANTED)
                    {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                        textBLS!!.text = "블루투스 활성화"
                    }
                    else{
                        textBLS!!.text = "블루투스 Off"
                }
            }
        }
    }
}