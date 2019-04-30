package com.robining.webrtcdemo;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
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
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String VIDEO_TRACK_ID = "";

    private SurfaceViewRenderer localSurfaceViewRenderer;
    private SurfaceViewRenderer remoteSurfaceViewRenderer;
    private EglBase eglBase = EglBase.create();
    private VideoCapturer localVideoCapturer;

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
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .createPeerConnectionFactory();

        localSurfaceViewRenderer.post(() -> {
            VideoSource localVideoSource = peerConnectionFactory.createVideoSource(false);

            localVideoCapturer = createVideoCapture();
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("captureThread", eglBase.getEglBaseContext());
            localVideoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), localVideoSource.getCapturerObserver());

            VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);
            videoTrack.addSink(localSurfaceViewRenderer);
            videoTrack.setEnabled(true);

            localVideoCapturer.startCapture(localSurfaceViewRenderer.getWidth(), localSurfaceViewRenderer.getHeight(), 15000);
        });

        remoteSurfaceViewRenderer.post(() -> {
            VideoSource remoteVideoSource = peerConnectionFactory.createVideoSource(false);
            VideoTrack remoteVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, remoteVideoSource);
            remoteVideoTrack.addSink(remoteSurfaceViewRenderer);
            remoteVideoTrack.setEnabled(true);

            List<PeerConnection.IceServer> servers = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            urls.add("stun:stun.l.google.com:19302");
            urls.add("stun:stun.ekiga.net");
            PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(urls).createIceServer();
            servers.add(iceServer);
            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(servers);
            PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfiguration, new SimplePeerConnectionObserver());
            if (peerConnection == null) {
                Toast.makeText(this, "远程连接失败", Toast.LENGTH_SHORT).show();
                return;
            }

            MediaConstraints mediaConstraints = new MediaConstraints();
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
            peerConnection.createOffer(new SimpleSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, sessionDescription.description, Toast.LENGTH_SHORT).show());
//                    peerConnection.setLocalDescription(this, sessionDescription);
                    //TODO 上传到服务器
                }
            }, mediaConstraints);
            peerConnection.addTrack(remoteVideoTrack);
        });
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

        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }

    private class SimplePeerConnectionObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {

        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }
}
