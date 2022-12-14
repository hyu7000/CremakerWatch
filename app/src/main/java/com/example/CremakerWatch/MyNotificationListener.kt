package com.example.CremakerWatch

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.graphics.drawable.toBitmap
import com.example.CremakerWatch.NotificationAppList.Companion.isInitInstanceOfNoti
import java.io.ByteArrayOutputStream

var preSendMsg: String = ""

class MyNotificationListener: NotificationListenerService()  {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e("kobbi","MyNotificationListener.onListenerConnected()")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e("kobbi","MyNotificationListener.onListenerDisconnected()")
    }

    var largeIcon: Icon? = null

    @SuppressLint("NewApi")
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if(!isConnectedBLE) return

        val notification = sbn!!.notification
        val extras = sbn!!.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)

        largeIcon = notification.getLargeIcon()
        //convertAndSendBitmapToArray()

        Log.d("cw_test"," title: " + title)
        Log.d("cw_test"," text : " + text)
        Log.d("cw_test"," subText: " + subText)
        Log.d("cw_test"," channelId: " + notification.channelId)
        Log.d("cw_test"," group: " + notification.group)
        Log.d("cw_test"," packageName: " + sbn.packageName )

        if(isInitInstanceOfNoti) {
            sbn?.packageName?.run {

                var sendMsg = arrayOfNulls<String>(4)
                sendMsg[0] = "MS T:" + title
                sendMsg[1] = "MS C:" + text
                sendMsg[2] = "MS S:" + subText
                sendMsg[3] = "MS O"

                if(title.toString() == "null") return@run
                else {
                    Log.d("cw_test_Msg", "title : " + title.toString())
                }

                var isThereSendPackageList =
                    NotificationAppList.instanceNotiList.checkValueOfAppList(sbn.packageName)

                var delayTime: Long = 0

                for (i in 0..3) {
                    if (isThereSendPackageList && preSendMsg != sendMsg[i]) {
                        MainActivity.instance.sendMsgToBLEDevice(sendMsg[i].toString(), true)
                        preSendMsg = sendMsg[i].toString()
                    } else {
                        Log.d("cw_test_Msg", "Noti 필터됨")
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun convertAndSendBitmapToArray() {
        if(largeIcon == null) {
            Log.d("cw_test","largeIcon is null")
            return
        }

        var iconBitmap : Drawable? = largeIcon?.loadDrawable(applicationContext)
        var bitmap = iconBitmap?.toBitmap(100,100)
        var streamForBitmap = ByteArrayOutputStream()
        bitmap?.compress( Bitmap.CompressFormat.JPEG,100, streamForBitmap)

        var byteArray = streamForBitmap?.toByteArray()

        MainActivity.instance.sendMsgToBLEDevice(byteArray)

    }
}