package com.example.CremakerWatch

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

var latitudeValue : Double = 0.0
var longitudeValue : Double = 0.0

var isGetLocationValue = false

class GetLocation {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @SuppressLint("MissingPermission")
    fun getLocation(isFirst: Boolean = true){
        if(isFirst) fusedLocationClient = LocationServices.getFusedLocationProviderClient(MainActivity.instance)

        fusedLocationClient.getLastLocation()

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
//                Log.d("cw_test", "위치 : " + location.toString())
//                Log.d("cw_test", "위도 : " + location?.latitude)
//                Log.d("cw_test", "경도 : " + location?.longitude)
                Log.d("cw_test", "위치 확인")

                latitudeValue  = location?.latitude
                longitudeValue = location?.longitude

                isGetLocationValue = true

            } else {
                Log.d("cw_test", "location is Null")
            }
        }

        fusedLocationClient.lastLocation.addOnFailureListener {
            Log.d("cw_test", "위치 정보를 가져오지 못함" + it.toString())
        }
    }
}