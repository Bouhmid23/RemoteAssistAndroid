package com.codewithkael.webrtcprojectforrecord

import android.util.Log
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import com.codewithkael.webrtcprojectforrecord.utils.NewMessageInterface
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener


class SocketRepository (private val messageInterface: NewMessageInterface) {
    private var webSocket: WebSocket? = null
    private var userName: String? = null
    private val tag = "SocketRepository"
    private val gson = Gson()

    fun initSocket(username: String) {
        userName = username
        val client = OkHttpClient()
        val request = Request.Builder().url("wss://signaling-server-keuc.onrender.com").build()
        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendMessageToSocket(
                    MessageModel(
                        "login",username,null,null))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    messageInterface.onNewMessage(gson.fromJson(text,MessageModel::class.java))
                }catch (e:Exception){
                    println("Exception raised : $e")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "onClose: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d(tag, "onError: $t")
                onOpen(webSocket,response!!)
            }
        }
        webSocket = client.newWebSocket(request,webSocketListener)

    }

    fun sendMessageToSocket(message: MessageModel) {
        try {
            val payload = Gson().toJson(message)
            Log.d(tag, "sendMessageToSocket:MessageModel $message, JSON: $payload")
            webSocket?.send(payload)
        } catch (e: Exception) {
            Log.d(tag, "sendMessageToSocket: $e")
        }
    }
}