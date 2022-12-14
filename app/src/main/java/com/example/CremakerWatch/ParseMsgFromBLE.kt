package com.example.CremakerWatch

class ParseMsgFromBLE {
    private var mainCodeMsg: String =""
    private var subCodeMsg: String = ""

    fun parseMsg(msg:String){
        when(msg[0].toString()) {
            "M" -> {
                when(msg.substring(0 until 2)) {
                    "11" -> {

                    }
                }
            }
        }
    }
}