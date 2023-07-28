package com.codewithkael.webrtcprojectforrecord


import android.app.Application
import com.codewithkael.webrtcprojectforrecord.models.MessageModel
import org.webrtc.*
import org.webrtc.PeerConnection.RTCConfiguration


class RTCClient(
    private val application: Application,
    private val username: String,
    private val socketRepository: SocketRepository,
    private val observer: PeerConnection.Observer
) {

    init {
        initPeerConnectionFactory(application)
    }

    private val eglContext = EglBase.create()
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val iceServer = listOf(

        PeerConnection.IceServer.builder("stun:stun.1.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp").setPassword("openrelayproject").setUsername("openrelayproject").createIceServer(),)

    private val rtcConfig = RTCConfiguration(iceServer)
    private lateinit var videoCapturer: CameraVideoCapturer
    private val peerConnection by lazy { createPeerConnection(rtcConfig,observer) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(videoCapturer.isScreencast) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null


    private fun initPeerConnectionFactory(application: Application) {
        val peerConnectionOption = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(peerConnectionOption)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglContext.eglBaseContext,
                    true,
                    true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = true
                networkIgnoreMask=0
            }).createPeerConnectionFactory()
    }

    private fun createPeerConnection(
        rtcConfiguration: RTCConfiguration,
        observer: PeerConnection.Observer): PeerConnection? {
        rtcConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfiguration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfiguration.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfiguration.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
        rtcConfiguration.iceTransportsType = PeerConnection.IceTransportsType.ALL
        rtcConfiguration.enableDtlsSrtp = true
        return peerConnectionFactory.createPeerConnection(rtcConfiguration, observer)
    }

    fun initializeSurfaceView(surface: SurfaceViewRenderer) {
        surface.run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(eglContext.eglBaseContext, null)
        }
    }

    fun startLocalVideo(surface: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglContext.eglBaseContext)
        videoCapturer = getVideoCapturer(application)
        videoCapturer.initialize(
            surfaceTextureHelper,
            surface.context, localVideoSource!!.capturerObserver)
        videoCapturer.startCapture(320, 240, 30)
        localVideoTrack = peerConnectionFactory.createVideoTrack("local_track", localVideoSource)
        localVideoTrack?.addSink(surface)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack("local_track_audio", localAudioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))
        peerConnection?.addTrack(localVideoTrack, listOf("local_stream"))
    }

    private fun getVideoCapturer(application: Application): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw
            IllegalStateException()
        }
    }

    fun call(target: String) {
        val mediaConstraints = MediaConstraints()
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        socketRepository.sendMessageToSocket(
                            MessageModel("offer", username, target, offer))
                    }

                    override fun onCreateFailure(p0: String?) {}

                    override fun onSetFailure(p0: String?) {}

                }, desc)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(session: SessionDescription,target: String) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {

            }

            override fun onSetSuccess() {
                socketRepository.sendMessageToSocket(MessageModel("ready",username,target))
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, session)

    }

    fun answer(target: String) {
        val constraints = MediaConstraints()
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }


                    override fun onSetSuccess() {
                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                "answer", username, target, answer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                    }

                }, desc)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, constraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection?.addIceCandidate(p0)
    }

    fun switchCamera() {
        videoCapturer.switchCamera(null)
    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleCamera(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun endCall() {
        val audioTrackToRemove :AudioTrack = localAudioTrack!!
        val videoTrackToRemove :VideoTrack = localVideoTrack!!
        val rtpAudioSender : RtpSender? = peerConnection?.senders?.find { it.track() == audioTrackToRemove }
        val rtpVideoSender : RtpSender? = peerConnection?.senders?.find { it.track() == videoTrackToRemove }
        if(rtpVideoSender !=null && rtpAudioSender!=null){
            peerConnection?.removeTrack(rtpAudioSender)
            peerConnection?.removeTrack(rtpVideoSender)
            peerConnection?.close()}
    }
}