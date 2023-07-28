package com.codewithkael.webrtcprojectforrecord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import com.codewithkael.webrtcprojectforrecord.databinding.ActivityCallBinding
import com.codewithkael.webrtcprojectforrecord.ml.SsdMobilenetV11Metadata1
import com.codewithkael.webrtcprojectforrecord.models.IceCandidateModel
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import com.codewithkael.webrtcprojectforrecord.models.MyAdapter
import com.codewithkael.webrtcprojectforrecord.models.User
import com.codewithkael.webrtcprojectforrecord.utils.NewMessageInterface
import com.codewithkael.webrtcprojectforrecord.utils.PeerConnectionObserver
import com.codewithkael.webrtcprojectforrecord.utils.RTCAudioManager
import com.google.gson.Gson
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.ArrayList


class CallActivity : AppCompatActivity(), NewMessageInterface {

    lateinit var binding : ActivityCallBinding
    private var userName:String?=null
    private var socketRepository:SocketRepository?=null
    private var rtcClient : RTCClient?=null
    private val tag = "CallActivity"
    private var target:String = ""
    private val gson = Gson()
    private var isMute = false
    private var isCameraPause = false
    private val rtcAudioManager by lazy { RTCAudioManager.create(this) }
    private var isSpeakerMode = true
    private  var isObjectDetection = true
    private lateinit var listView: ListView
    private lateinit var userArrayList: ArrayList<User>
    private var isLocalViewInitialized = false
    private var isRemoteViewInitialized = false
    private var paint = Paint()
    private lateinit var bitmap : Bitmap
    private lateinit var model : SsdMobilenetV11Metadata1
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var labels:List<String>
    private var colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        labels = FileUtil.loadLabels(this,"labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        listView= findViewById(R.id.listView)
        init()
    }

    private fun init(){
        userName = intent.getStringExtra("username")
        socketRepository = SocketRepository(this)
        userName?.let { socketRepository?.initSocket(it) }
        rtcClient = RTCClient(application,userName!!,socketRepository!!, object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                rtcClient?.addIceCandidate(p0)
                val candidate = hashMapOf(
                    "sdpMid" to p0?.sdpMid,
                    "sdpMLineIndex" to p0?.sdpMLineIndex,
                    "candidate" to p0?.sdp)
                socketRepository?.sendMessageToSocket(
                    MessageModel("candidate",userName,target,candidate))
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                super.onAddTrack(p0, p1)
                if (p1 != null && p1.isNotEmpty()) {
                    val videoTracks: List<VideoTrack> = p1.flatMap { it.videoTracks }
                    if (videoTracks.isNotEmpty()) {
                        val videoTrack: VideoTrack = videoTracks[0]
                        videoTrack.addSink(binding.remoteView)
                        Log.d(tag, "onAddTrack: $videoTrack")
                    }
                }
            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                super.onIceConnectionChange(p0)
                Log.d(tag,"iceConnection State : $p0")
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
            }
        })
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        binding.apply {
            callBtn.setOnClickListener {
                target = targetUserNameEt.text.toString()
                socketRepository?.sendMessageToSocket(MessageModel(
                    "want_to_call",target,null,null
                ))
            }
            switchCameraButton.setOnClickListener {
                rtcClient?.switchCamera()
            }
            micButton.setOnClickListener {
                if (isMute){
                    isMute = false
                    micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
                }else{
                    isMute = true
                    micButton.setImageResource(R.drawable.ic_baseline_mic_24)
                }
                rtcClient?.toggleAudio(isMute)
            }
            videoButton.setOnClickListener {
                if (isCameraPause){
                    isCameraPause = false
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                }else{
                    isCameraPause = true
                    videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                }
                rtcClient?.toggleCamera(isCameraPause)
            }
            audioOutputButton.setOnClickListener {
                if (isSpeakerMode){
                    isSpeakerMode = false
                    audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
                    rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
                }else{
                    isSpeakerMode = true
                    audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                    rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
                }
            }
            endCallButton.setOnClickListener {
                setCallLayoutGone()
                setWhoToCallLayoutVisible()
                setIncomingCallLayoutGone()
                rtcClient?.endCall()
                socketRepository?.sendMessageToSocket(MessageModel("leave",target))
            }
            objectDetectionButton.setOnClickListener {
                    Log.d(tag,"object detection button clicked")
                    if(isObjectDetection){
                        objectDetectionButton.setImageResource(R.drawable.object_detection_off)
                        isObjectDetection = false
                        runOnUiThread {
                            remoteView.addFrameListener({
                                Log.d(tag,"object detection button in on frame listener")
                                bitmap = it
                                var image = TensorImage.fromBitmap(bitmap)
                                image = imageProcessor.process(image)
                                val outputs = model.process(image)
                                val locations = outputs.locationsAsTensorBuffer.floatArray
                                val classes = outputs.classesAsTensorBuffer.floatArray
                                val scores = outputs.scoresAsTensorBuffer.floatArray
                                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                                val canvas = Canvas(mutable)
                                objectDetection.post {
                                    draw(mutable,locations,classes,scores,canvas,objectDetection)
                                    objectDetection.visibility = View.VISIBLE
                                }
                            }, 2.0F)
                        }
                    }else{
                        objectDetectionButton.setImageResource(R.drawable.object_detection)
                        isObjectDetection = true
                        objectDetection.visibility = View.GONE
                    }
                }
                }


    }
    private fun draw(mutable:Bitmap, locations:FloatArray,
                     classes:FloatArray, scores: FloatArray,
                     canvas: Canvas, imageView : ImageView){

        val h = mutable.height
        val w = mutable.width
        paint.textSize = h/15f
        paint.strokeWidth = h/85f
        var x: Int
        scores.forEachIndexed { index, fl ->
            x = index
            x *= 4
            if(fl > 0.5){
                paint.color = colors[index]
                paint.style = Paint.Style.STROKE
                canvas.drawRect(RectF(locations[x + 1] *w, locations[x] *h, locations[x + 3] *w, locations[x + 2] *h), paint)
                paint.style = Paint.Style.FILL
                canvas.drawText(labels[classes[index].toInt()] +" "+fl.toString(), locations[x+1] *w, locations[x] *h, paint)
            }
        }
        imageView.setImageBitmap(mutable)
    }

    override fun onNewMessage(message: MessageModel) {
        Log.d(tag, "onNewMessage: $message")
        when(message.type){
            "server_already_in_room"->{
                if (message.success == true){
                    runOnUiThread {
                        Toast.makeText(this,"user is not reachable",Toast.LENGTH_LONG).show()
                    }
                }else{
                    runOnUiThread {
                        setWhoToCallLayoutGone()
                        setCallLayoutVisible()
                        binding.apply {
                            isLocalViewInitialized = if(!isLocalViewInitialized){
                                rtcClient?.initializeSurfaceView(localView)
                                true
                            } else{
                                localView.release()
                                false
                            }
                            isRemoteViewInitialized = if(!isRemoteViewInitialized){
                                rtcClient?.initializeSurfaceView(remoteView)
                                true
                            } else{
                                remoteView.release()
                                false
                            }
                            rtcClient?.startLocalVideo(localView)
                            rtcClient?.call(targetUserNameEt.text.toString())
                            Log.d("server_already_in_room","Call launched" +
                                    " et isRemoteViewInitialized: $isRemoteViewInitialized" +
                                    " et isLocalViewInitialized: $isLocalViewInitialized")
                        }
                    }
                }
            }
            "server_user_list"->{
                try {
                    runOnUiThread {
                        val list = message.name as ArrayList<*>
                        val nameList = ArrayList<String>()
                        val stateList = ArrayList<String>()
                        userArrayList = ArrayList()
                        for(i in list.indices){
                            val item = list[i] as List<*>
                            if(item[0].toString()!=userName){
                                nameList.add(item[0].toString())
                                stateList.add(item[1].toString())
                                val user = User(item[0].toString(),item[1].toString())
                                userArrayList.add(user)}
                        }
                        binding.listView.adapter = MyAdapter(this,userArrayList)
                        if (R.id.state.toString() == "online"){
                            R.id.state
                        }
                        binding.listView.setOnItemClickListener { _, _, position, _ ->
                            binding.targetUserNameEt.setText(nameList[position])
                        }
                    }

                }catch (e:Exception){
                    e.printStackTrace()
                }

            }
            "server_answer" ->{
                val session = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    message.data.toString())
                rtcClient?.onRemoteSessionReceived(session,target)
                runOnUiThread {
                    binding.remoteViewLoading.visibility = View.GONE
                }
            }
            "server_offer" ->{
                runOnUiThread {
                    val isCallingYou ="${message.name.toString()} is calling you"
                    setIncomingCallLayoutVisible()
                    binding.incomingNameTV.text = isCallingYou
                    binding.acceptButton.setOnClickListener {
                        setIncomingCallLayoutGone()
                        setCallLayoutVisible()
                        setWhoToCallLayoutGone()

                        binding.apply {
                            if(!isLocalViewInitialized){
                                rtcClient?.initializeSurfaceView(localView)
                                rtcClient?.startLocalVideo(localView)
                                isLocalViewInitialized = true}
                            if(!isRemoteViewInitialized){
                                rtcClient?.initializeSurfaceView(remoteView)
                                isRemoteViewInitialized = true}
                        }
                        val session = SessionDescription(
                            SessionDescription.Type.OFFER,
                            message.data.toString())
                        target = message.name!!.toString()
                        rtcClient?.onRemoteSessionReceived(session,target)
                        rtcClient?.answer(message.name.toString())
                        binding.remoteViewLoading.visibility = View.GONE
                    }
                    binding.rejectButton.setOnClickListener {
                        setIncomingCallLayoutGone()
                        socketRepository?.sendMessageToSocket(MessageModel("busy",message.name.toString()))
                    }
                }
            }
            "server_candidate"->{
                try {
                    val receivingCandidate = gson.fromJson(gson.toJson(message.data),
                        IceCandidateModel::class.java)
                    rtcClient?.addIceCandidate(IceCandidate(receivingCandidate.sdpMid,
                        Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),receivingCandidate.candidate))
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }

            "server_busy_user" ->{
                runOnUiThread {
                    setCallLayoutGone()
                    setWhoToCallLayoutVisible()
                    setIncomingCallLayoutGone()
                    rtcClient?.endCall()
                    Toast.makeText(this,"Peer Rejected your call",Toast.LENGTH_LONG).show() }
                }
            "server_user_want_to_leave"->{
                runOnUiThread {
                    setCallLayoutGone()
                    setWhoToCallLayoutVisible()
                    setIncomingCallLayoutGone()
                    rtcClient?.endCall()
                    Toast.makeText(this,"Call ended ",Toast.LENGTH_LONG).show() }
            }
            "server_exit_from"->{
                runOnUiThread {
                    setCallLayoutGone()
                    setWhoToCallLayoutVisible()
                    setIncomingCallLayoutGone()
                    rtcClient?.endCall()
                    Toast.makeText(this,"User disconnected from server",Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun setIncomingCallLayoutGone(){
        binding.incomingCallLayout.visibility = View.GONE
    }
    private fun setIncomingCallLayoutVisible() {
        binding.incomingCallLayout.visibility = View.VISIBLE
    }

    private fun setCallLayoutGone() {
        binding.callLayout.visibility = View.GONE
    }

    private fun setCallLayoutVisible() {
        binding.callLayout.visibility = View.VISIBLE
    }

    private fun setWhoToCallLayoutGone() {
        binding.whoToCallLayout.visibility = View.GONE
    }

    private fun setWhoToCallLayoutVisible() {
        binding.whoToCallLayout.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }
    }
