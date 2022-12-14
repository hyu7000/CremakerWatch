package com.example.CremakerWatch

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.graphics.Point
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.lang.RuntimeException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val num_of_rows  = 60
val page_no      = 1
val data_type    = "JSON"
var base_time    = "0600"
var base_data    = "20220802"
var nx           = "55"
var ny           = "127"

val PTY_RES = 0
val REH_RES = 1
val RN1_RES = 2
val T1H_RES = 3
val UUU_RES = 4
val VEC_RES = 5
val VVV_RES = 6
val WSD_RES = 7

data class WEATHER (
    val response : RESPONSE
)
data class RESPONSE (
    val header : HEADER,
    val body : BODY
)
data class HEADER(
    val resultCode : Int,
    val resultMsg : String
)
data class BODY(
    val dataType : String,
    val items : ITEMS
)
data class ITEMS(
    val item : List<ITEM>
)
data class ITEM(
    val baseData : String,
    val baseTime : String,
    val category : String,
    val obsrValue : Double
)

data class WeatherDataToSsend(
    val precipitation : Double,
    val temperature : Double,
    val windSpeed : Double
)

interface WeatherInterface {
    @GET("getUltraSrtNcst?serviceKey=f4yAmExFGVEV0uRW49sWY9tCURKMW%2Bb31SZCkoN5H8UBN%2FUJazrv%2BaKRU0uCSHU9Mszm80Bg7rnyS6EqMfqsnw%3D%3D")
    fun GetWeather(
        @Query("numOfRows") num_of_rows : Int,
        @Query("pageNo") page_no : Int,
        @Query("dataType") data_type : String,
        @Query("base_date") base_date : String,
        @Query("base_time") base_time : String,
        @Query("nx") nx : String,
        @Query("ny") ny : String
    ): Call<WEATHER>
}

private val retrofit = Retrofit.Builder()
    .baseUrl("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

object ApiObject {
    val retrofitService: WeatherInterface by lazy {
        retrofit.create(WeatherInterface::class.java)
    }
}

class GetWeatherInfo {
    @RequiresApi(Build.VERSION_CODES.O)
    fun setDateTime() {
        val currentDate = LocalDateTime.now()
        val formatterDate = DateTimeFormatter.ofPattern("yyyyMMdd")
        val formattedDate = currentDate.format(formatterDate)

        var currentHour = currentDate.hour
        var currentMin  = currentDate.minute

        if(currentMin < 30) currentHour--

        base_data = formattedDate
        base_time = currentHour.toString() + "00"

        Log.d("cw_test","data : " + base_data + ", time : " + base_time)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun askAPIWeather() {

        if(!isConnectedBLE) return

        setDateTime()

        val call = ApiObject.retrofitService.GetWeather(num_of_rows, page_no, data_type , base_data, base_time, nx, ny)

        call.enqueue(object : retrofit2.Callback<WEATHER>{
            override fun onResponse(call: Call<WEATHER>, response: Response<WEATHER>) {
                if (response.isSuccessful){
                    try {
//                        Log.d("api", " 2: " + response.body()!!.response.body.items.item.toString())
//                        Log.d("api"," 3: " + response.body()!!.response.body.items.item[0].obsrValue)

                        var weatherDataToSsend = WeatherDataToSsend(
                            response.body()?.response!!.body.items.item[PTY_RES].obsrValue,
                            response.body()?.response!!.body.items.item[T1H_RES].obsrValue,
                            response.body()?.response!!.body.items.item[WSD_RES].obsrValue
                        )
//
                        var mainActivity = MainActivity()
                        mainActivity.sendMsgToBLEDevice(
                            "WD PTY" + weatherDataToSsend.precipitation.toString(), true //강수형태
                        ) // 이 부분은 가독성을 위해 분리할 것

                        mainActivity.sendMsgToBLEDevice(
                            "WD T1H" + weatherDataToSsend.temperature.toString(), true //기온
                        ) // 이 부분은 가독성을 위해 분리할 것

                        Log.d("cw_test_weather", "pre"+weatherDataToSsend.precipitation.toString() + ", tem"+weatherDataToSsend.temperature.toString() + ", ws"+weatherDataToSsend.windSpeed.toString())
                    }
                    catch (e: RuntimeException) {
                        Log.d("cw_test_weather","API를 얻어오지 못함, Err :" + e.toString())
                    }
                }
            }
            override fun onFailure(call: Call<WEATHER>, t: Throwable) {
                Log.d("api fail : ", t.message.toString())
            }
        })
    }

    fun calXYFromGPS(latitude: Double, longitude: Double) : Point {
        val RE = 6371.00877     // 지구 반경(km)
        val GRID = 5.0          // 격자 간격(km)
        val SLAT1 = 30.0        // 투영 위도1(degree)
        val SLAT2 = 60.0        // 투영 위도2(degree)
        val OLON = 126.0        // 기준점 경도(degree)
        val OLAT = 38.0         // 기준점 위도(degree)
        val XO = 43             // 기준점 X좌표(GRID)
        val YO = 136            // 기준점 Y좌표(GRID)
        val DEGRAD = Math.PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD

        var sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5)
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn)
        var sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5)
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn
        var ro = Math.tan(Math.PI * 0.25 + olat * 0.5)
        ro = re * sf / Math.pow(ro, sn)

        var ra = Math.tan(Math.PI * 0.25 + (latitude) * DEGRAD * 0.5)
        ra = re * sf / Math.pow(ra, sn)
        var theta = longitude * DEGRAD - olon
        if (theta > Math.PI) theta -= 2.0 * Math.PI
        if (theta < -Math.PI) theta += 2.0 * Math.PI
        theta *= sn

        val x = (ra * Math.sin(theta) + XO + 0.5).toInt()
        val y = (ro - ra * Math.cos(theta) + YO + 0.5).toInt()

        nx = x.toString()
        ny = y.toString()

        Log.d("cw_test","xy그리드 좌표 x: " + x.toString() + " y: "+ y.toString())

        return Point(x, y)
    }
}