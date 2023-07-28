package com.codewithkael.webrtcprojectforrecord.models

data class MessageModel(
     val type: String,
     val name: Any? = null,
     val target: String? = null,
     val data:Any?=null,
     val success:Boolean?=null

)
