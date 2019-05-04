package com.robining.webrtcdemo;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import org.webrtc.BuiltinAudioDecoderFactoryFactory;
import org.webrtc.BuiltinAudioEncoderFactoryFactory;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String VIDEO_TRACK_ID = "1";

    private SurfaceViewRenderer localSurfaceViewRenderer;
    private SurfaceViewRenderer remoteSurfaceViewRenderer;
    private EglBase eglBase = EglBase.create();
    private VideoCapturer localVideoCapturer;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection localPeerConnection;
    private PeerConnection remotePeerConnection;
    private VideoSource localVideoSource, remoteVideoSource;
    private VideoTrack localVideoTrack, remoteVideoTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        localSurfaceViewRenderer = findViewById(R.id.localViewRender);
        localSurfaceViewRenderer.init(eglBase.getEglBaseContext(), null);

        remoteSurfaceViewRenderer = findViewById(R.id.remoteViewRender);
        remoteSurfaceViewRenderer.init(eglBase.getEglBaseContext(), null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PermissionUtil.getManifestShouldRequestPermissions(this).toArray(new String[]{}), 1000);
        } else {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        init();
    }

    private void init() {
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                .setEnableInternalTracer(true)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setAudioEncoderFactoryFactory(new BuiltinAudioEncoderFactoryFactory())
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .setAudioDecoderFactoryFactory(new BuiltinAudioDecoderFactoryFactory())
                .createPeerConnectionFactory();

        localSurfaceViewRenderer.post(() -> {
            localVideoSource = peerConnectionFactory.createVideoSource(false);

            localVideoCapturer = createVideoCapture();
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("captureThread", eglBase.getEglBaseContext());
            localVideoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), localVideoSource.getCapturerObserver());

            localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);
            localVideoTrack.addSink(localSurfaceViewRenderer);
            localVideoTrack.setEnabled(true);

            localVideoCapturer.startCapture(localSurfaceViewRenderer.getWidth(), localSurfaceViewRenderer.getHeight(), 30);

            localPeerConnection = createLocalPeerConnection();
            if (localPeerConnection == null) {
                Toast.makeText(this, "创建连接失败:LOCAL", Toast.LENGTH_SHORT).show();
                return;
            }

            localPeerConnection.addTrack(localVideoTrack);

            tryBuildConnection();
        });

        remoteSurfaceViewRenderer.post(() -> {
//            remoteVideoSource = peerConnectionFactory.createVideoSource(false);
//            remoteVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, remoteVideoSource);
//            remoteVideoTrack.addSink(remoteSurfaceViewRenderer);
//            remoteVideoTrack.setEnabled(true);

            remotePeerConnection = createRemotePeerConnection();
            if (remotePeerConnection == null) {
                Toast.makeText(this, "创建连接失败:REMOTE", Toast.LENGTH_SHORT).show();
                return;
            }
//            remotePeerConnection.addTrack(remoteVideoTrack);

            tryBuildConnection();
        });
    }

    /**
     * 由本地PeerConnection创建Offer
     * 由远程PeerConnection篡改就Answer
     */
    private void tryBuildConnection() {
        if (remotePeerConnection == null || localPeerConnection == null) {
            return;
        }
        initLocalConnectionOffer();
    }

    private void initLocalConnectionOffer() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        localPeerConnection.createOffer(new SimpleSdpObserver() {
            private SessionDescription offerSessionDescription;

            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                System.out.println(">>>本地连接创建Offer成功");
                offerSessionDescription = sessionDescription;
                localPeerConnection.setLocalDescription(this, offerSessionDescription);
            }

            @Override
            public void onSetSuccess() {
                System.out.println(">>>本地连接配置LocalDescription成功");
                remotePeerConnection.setRemoteDescription(new SimpleSdpObserver() {
                    private SessionDescription answerSessionDescription;

                    @Override
                    public void onSetSuccess() {
                        System.out.println(">>>远程连接配置RemoteDescription成功");
                        remotePeerConnection.createAnswer(this, mediaConstraints);
                    }

                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        System.out.println(">>>远程连接创建Answer成功");
                        this.answerSessionDescription = sessionDescription;
                        remotePeerConnection.setLocalDescription(new SimpleSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                System.out.println(">>>远程连接配置LocalDescription成功");

                                localPeerConnection.setRemoteDescription(new SimpleSdpObserver() {
                                    @Override
                                    public void onSetSuccess() {
                                        System.out.println(">>>本地连接配置RemoteDescription成功");
                                    }
                                }, answerSessionDescription);
                            }
                        }, sessionDescription);
                    }
                }, offerSessionDescription);

            }
        }, mediaConstraints);
    }

    private PeerConnection createLocalPeerConnection() {
        return createPeerConnection(new SimplePeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                System.out.println(">>>本地连接IceCandidate收集成功，配置到远程连接");
                remotePeerConnection.addIceCandidate(iceCandidate);
            }
        });
    }

    private PeerConnection createRemotePeerConnection() {
        return createPeerConnection(new SimplePeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                System.out.println(">>>远程连接IceCandidate收集成功，配置到本地连接");
                localPeerConnection.addIceCandidate(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                if(mediaStream.videoTracks != null && mediaStream.videoTracks.size() > 0){
                    VideoTrack videoTrack = mediaStream.videoTracks.get(0);
                    videoTrack.addSink(remoteSurfaceViewRenderer);
                    videoTrack.setEnabled(true);
                }
            }
        });
    }

    private PeerConnection createPeerConnection(PeerConnection.Observer observer) {
        List<PeerConnection.IceServer> servers = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        urls.add("stun:stun.l.google.com:19302");
        urls.add("stun:stun.ekiga.net");
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(urls).createIceServer();
        servers.add(iceServer);
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(servers);
        return peerConnectionFactory.createPeerConnection(rtcConfiguration, observer);
    }

    private VideoCapturer createVideoCapture() {
        if (Camera2Enumerator.isSupported(this)) {
            return createVideoCapture(new Camera2Enumerator(this));
        } else {
            return createVideoCapture(new Camera1Enumerator(true));
        }
    }

    private VideoCapturer createVideoCapture(CameraEnumerator cameraEnumerator) {
        String[] deviceNames = cameraEnumerator.getDeviceNames();
        if (deviceNames == null || deviceNames.length == 0) {
            Toast.makeText(MainActivity.this, "没有摄像头支持", Toast.LENGTH_SHORT).show();
            throw new RuntimeException("not found supported camera");
        }

        String selectedDeviceName = deviceNames[0];
        if (cameraEnumerator.isFrontFacing(selectedDeviceName)) {
            Toast.makeText(this, "使用前置摄像头", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "使用后置摄像头", Toast.LENGTH_SHORT).show();
        }
        return cameraEnumerator.createCapturer(selectedDeviceName, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localVideoCapturer != null) {
            try {
                localVideoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class SimpleSdpObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            System.out.println(">>>onCreateSuccess:" + sessionDescription.type);
        }

        @Override
        public void onSetSuccess() {
            System.out.println(">>>onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
            System.out.println(">>>onCreateFailure:" + s);
        }

        @Override
        public void onSetFailure(String s) {
            System.out.println(">>>onSetFailure:" + s);
        }
    }

    private class SimplePeerConnectionObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            System.out.println(">>>onSignalingChange:" + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            System.out.println(">>>onIceConnectionChange:" + iceConnectionState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            System.out.println(">>>onIceConnectionReceivingChange:" + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            System.out.println(">>>onIceGatheringChange:" + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            System.out.println(">>>onIceCandidate:" + iceCandidate.toString());
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            System.out.println(">>>onIceCandidatesRemoved");
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            System.out.println(">>>onAddStream:" + mediaStream.getId());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            System.out.println(">>>onRemoveStream:" + mediaStream.getId());
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            System.out.println(">>>onDataChannel:" + dataChannel.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            System.out.println(">>>onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            System.out.println(">>>onAddTrack:" + rtpReceiver.id());
        }
    }
}
