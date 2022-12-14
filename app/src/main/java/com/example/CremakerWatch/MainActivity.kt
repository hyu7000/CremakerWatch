package com.example.CremakerWatch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import android.bluetooth.le.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.*
import android.telecom.Call.Details.hasProperty
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.textfield.TextInputEditText
import java.lang.reflect.Method
import java.util.*


var isConnectedBLE  = false

var bluetoothDevice: BluetoothDevice? = null
var bluetoothGatt: BluetoothGatt?     = null

var curWriteCharacteristic: BluetoothGattCharacteristic?  = null
var curNotifyCharacteristic: BluetoothGattCharacteristic? = null

var connectedDevice : SharedPreferences? = null

class MainActivity : AppCompatActivity() {

    companion object{
        lateinit var instance: MainActivity
        var getLocationValue = GetLocation()
        lateinit var sendToAlarmList : SharedPreferences

    }

    init {
        instance = this
    }

    var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    var connectedDeviceName = ""

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
    var parsemsg = ParseMsgFromBLE()
    var sendMsgPeriodically = SendMsgPeriodically()

    private lateinit var getResultText: ActivityResultLauncher<Intent>

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // 앱리스트 클래스 생성
        var test = NotificationAppList()

        // 블루투스
        getResultText = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        }

        sendToAlarmList = getSharedPreferences("appListToSendMsg", MODE_PRIVATE)
        connectedDevice = getSharedPreferences("connectedDeviceList", MODE_PRIVATE)

        // 글로벌 변수에 현재 경도 위도 값을 얻고 저장함
        getLocationValue?.getLocation()

        // 백그라운드 서비스 시작
        val bgIntent = Intent(this, SendMsgServiceBackground::class.java)
        startService(bgIntent)

        // 각 컴포넌트 초기화, 초기설정
        textBLS          = findViewById(R.id.bluetooth_State) as TextView
        recLabelTextView = findViewById(R.id.rec_Label_Textview) as TextView
        recMsgTextView   = findViewById(R.id.rec_Msg_Textview) as TextView

        recLabelTextView?.visibility = View.VISIBLE
        recMsgTextView?.visibility   = View.VISIBLE

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
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE),98)
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

        val exitBtn: Button = findViewById(R.id.exitBGBtn)
        exitBtn.setOnClickListener {
            stopService(bgIntent)
            Log.d("cw_test","백그라운드 종료")
        }


        //BLE 스캔 시작 버튼의 동작 구성, leScanCallback 함수를 콜백
        var scanBLEBtn: Button = findViewById(R.id.BLE_Scan_Btn)
        val filters: MutableList<ScanFilter> = ArrayList()
        val scanFilter: ScanFilter = ScanFilter.Builder()
            .build()
        filters.add(scanFilter)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setLegacy(false)
            .build()
        scanBLEBtn.setOnClickListener {
            Handler(Looper.getMainLooper()).postDelayed({
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            }, 20000)

            bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, leScanCallback)
            textBLS?.text = "블루투스 BLE 찾는 중"
        }

        // 연결해제 버튼의 동작 설정
        var disconnectBtn: Button = findViewById(R.id.disconnect_BLE)
        disconnectBtn.setOnClickListener {
            if (bluetoothGatt != null) {
                bluetoothGatt?.disconnect()
                //bluetoothGatt?.close()
                isConnectedBLE = false
                textBLS?.text = "연결 해제됨"
            }
        }

        // 리스트뷰 선택시 블루투스 연결 및 스캔 중지
        val list_scaned_Bt: ListView = findViewById(R.id.scanedBt_ListView) as ListView
        list_scaned_Bt.adapter = ArrayAdapter(this,android.R.layout.simple_list_item_1, btListItem)
        list_scaned_Bt.setOnItemClickListener { parent, view, position, id ->
            if(btListItem[position]==null) return@setOnItemClickListener
            if(!isConnectedBLE) {
                Toast.makeText(this@MainActivity,parent.getItemAtPosition(position).toString() + "에 연결을 시도합니다.", Toast.LENGTH_SHORT).show()
                list_scaned_Bt.adapter =
                    ArrayAdapter(this, android.R.layout.simple_list_item_1, btListItem)

                bluetoothGatt = devicesArr[position]?.connectGatt(this, true, gattCallback)
                bluetoothDevice = devicesArr[position]

                Log.d("cw_test", bluetoothDevice?.name.toString())
                textBLS?.text = "연결 시도 중 : " + bluetoothDevice?.name.toString()

                connectedDeviceName = bluetoothDevice?.name.toString()
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

    private fun refreshDeviceCache(gatt: BluetoothGatt) {
        try {
            val localMethod: Method? = gatt.javaClass.getMethod("refresh")
            if (localMethod != null) {
                localMethod.invoke(gatt)
            }
        } catch (localException: Exception) {
            Log.d("Exception", localException.toString())
        }
    }

    @SuppressLint("MissingPermission")
    fun tryToConnectBLE(deviceName:String){
        for (device in devicesArr){
            if(device.name == deviceName){
                bluetoothDevice = device
            }
        }
        Log.d("cw_test","tryToConnectBLE 실행됨")
        //bluetoothGatt = bluetoothDevice?.connectGatt(this,false,gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun sendMsgToBLEDevice(string: String) :Boolean {
        if(!isConnectedBLE) {
            Log.d("cw_test","BLE 연결 상태가 아님")
            return false
        }

        if(string != null && string != "null"){
            curWriteCharacteristic?.setValue(string)
            var result : Boolean? = bluetoothGatt?.writeCharacteristic(curWriteCharacteristic)
            Log.d("cw_test","send msg result : "+result)
            return true
        }
        
        return false
    }

    @SuppressLint("MissingPermission")
    fun sendMsgToBLEDevice(string: String, must:Boolean, waitTime:Long = 5000) :Boolean {
        if(!isConnectedBLE) {
            Log.d("cw_test","BLE 연결 상태가 아님")
            return false
        }

        if(string != null && string != "null"){
            curWriteCharacteristic?.setValue(string)
            var result: Boolean = false
            var time = System.currentTimeMillis()
            Log.d("cw_test",string)
            while(must && !result) {
                result = bluetoothGatt?.writeCharacteristic(curWriteCharacteristic)!!
                var gaptime = System.currentTimeMillis() - time
                if(gaptime > waitTime)
                {
                    Log.d("cw_test", "time out to send msg")
                    return false
                }
                //Log.d("cw_test", "send msg result : " + result + " time" + gaptime.toString() )
            }
            return true
        }

        return false
    }

    @SuppressLint("MissingPermission")
    fun sendMsgToBLEDevice(byteArray: ByteArray) :Boolean {
        if(!isConnectedBLE) {
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
    private val leScanCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object:ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d("scanCallback", "BLE Scan Failed : " + errorCode)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult> ?) {
            super.onBatchScanResults(results)
            results?.let {
                // results is not null
                for(result in it) {
                    if(!devicesArr.contains(result.device) && result.device.name!=null) devicesArr.add(result.device)
                }
            }
        }
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                // result is not null
                if(!devicesArr.contains(it.device) && it.device.name!=null) {
                    devicesArr.add(it.device)

                    btListItem[scaned_Bt_Count] = it.device.name.toString()
                    scaned_Bt_Count++
                    if(scaned_Bt_Count>20) scaned_Bt_Count = 0

                    val list_scaned_Bt: ListView = findViewById(R.id.scanedBt_ListView) as ListView
                    list_scaned_Bt.adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_list_item_1, btListItem)
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    intentAction = ACTION_GATT_CONNECTED
                    broadcastUpdate(intentAction)
                    isConnectedBLE = bluetoothGatt?.discoverServices()!!
                    Log.d("cw_test","discoverServices() 실행 됨" + isConnectedBLE.toString())
                    if(isConnectedBLE)
                        bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)

                    connectedDevice?.edit()?.putString("connectedBleDevice",connectedDeviceName)?.apply()
                    runOnUiThread {
                        textBLS?.text = "연결 됨_2"
                    }
//                    Log.d("cw_test","gatt.device.address_1 : " + gatt.device.address)
//                    Log.d("cw_test","gatt.device.bondState_1 : " + gatt.device.bondState.toString())
//                    Log.d("cw_test","gatt.device.type_1 : " + gatt.device.type.toString())


                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread {
                        textBLS?.text = "연결 끊김"
                    }
                    intentAction = ACTION_GATT_DISCONNECTED
                    broadcastUpdate(intentAction)

                    //bluetoothGatt?.disconnect()
                    //bluetoothGatt?.close()
                    isConnectedBLE = false
                    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                    pairedDevices?.forEach { device ->
                        val deviceName = device.name
                        val deviceHardwareAddress = device.address // MAC address
                        Log.d("cw_test","본딩 기기 : " + deviceName + ", 주소 : " + deviceHardwareAddress)
                    }
                    Log.d("cw_test","연결 끊김")

                    refreshDeviceCache(gatt)
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

                        if(hasWrite && hasNotify) { // && hasDescriptor) {
                            UUID_UART_SERVICE = service.uuid
                            Log.d("cw_test","ser UUID : "+UUID_UART_SERVICE.toString())
                            try {
                                val descriptor = curNotifyCharacteristic?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                                    ?.apply {
                                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    }
                                var testDes = gatt.writeDescriptor(descriptor)
                                Log.d("cw_test","descriptor : "+ descriptor.toString())
                                Log.d("cw_test", "testDes : $testDes")
                            }catch (e : Exception)
                            {
                                Log.d("cw_test", "error : $e")
                            }

                            runOnUiThread {
                                textBLS?.text = "연결 됨"
                            }
                        }
                    }
                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                    // 주기적으로 메세지 보내는 함수 시작
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendMsgPeriodically.sendMsgP()
                        }, 10000) // 10,000 = 10s
                    }
                }
                BluetoothGatt.GATT_FAILURE -> {
                    Log.d("cw_test","GATT_FAILURE : " + gatt.device.bondState.toString())
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
                runOnUiThread {
                    val data: ByteArray? = characteristic.value
                    val msg = characteristic.getIntValue(FORMAT_UINT8, 0)
                    if (data?.isNotEmpty() == true) {
                        val hexString: String = data.joinToString(separator = " ") {
                            String.format("%02X", it)
                        }
                        intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                        recMsgTextView?.text = hexString
                        parsemsg.parseMsg(hexString)
                    }
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
                        getResultText.launch(enableBtIntent)
                        textBLS?.text = "블루투스 활성화"
                    }
                    else{
                        textBLS?.text = "블루투스 Off"
                }
            }
        }
    }
}