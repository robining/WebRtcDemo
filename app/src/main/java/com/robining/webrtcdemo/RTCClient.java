package com.robining.webrtcdemo;

import android.content.Context;

import org.webrtc.BuiltinAudioDecoderFactoryFactory;
import org.webrtc.BuiltinAudioEncoderFactoryFactory;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;

/**
 * @Description:
 * @Author: luohf
 * @Email:496349136@qq.com
 * @CreateDate: 2019/5/4 13:29
 * @UpdateUser:
 * @UpdateDate:
 * @UpdateRemark:
 */
public class RTCClient {
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase eglBase = EglBase.create();

    public RTCClient(Context context) {
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context.getApplicationContext())
                .setEnableInternalTracer(true)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setAudioEncoderFactoryFactory(new BuiltinAudioEncoderFactoryFactory())
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .setAudioDecoderFactoryFactory(new BuiltinAudioDecoderFactoryFactory())
                .createPeerConnectionFactory();
    }
}
