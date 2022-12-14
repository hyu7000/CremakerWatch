package com.example.CremakerWatch

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.CremakerWatch.MainActivity.Companion.getLocationValue
import java.sql.Time
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
class SendMsgPeriodically {
    val getWeather = GetWeatherInfo()

    fun sendDateTimeMsg(){
        if(!isConnectedBLE) return

        val currentDate = LocalDateTime.now()
        val formatterDate = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        var formatted = currentDate.format(formatterDate)
        formatted = "YD " + formatted

        MainActivity.instance.sendMsgToBLEDevice(formatted)
    }

    fun sendWeatherData(){
        if(!isConnectedBLE) return

        getLocationValue.getLocation(false)

        getWeather.calXYFromGPS(latitudeValue, longitudeValue)
        getWeather.askAPIWeather()
    }

    fun sendMsgP(time: Long = 600000){
        sendDateTimeMsg()
        sendWeatherData()

        repeatFunAfterTime(time)
    }

    private fun repeatFunAfterTime(time: Long){
        Handler(Looper.getMainLooper()).postDelayed({
            sendMsgP(time)
        }, time) // 600,000 = 10min
    }
}