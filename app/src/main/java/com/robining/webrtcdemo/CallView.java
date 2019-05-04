package com.robining.webrtcdemo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.widget.FrameLayout;

/**
 * @Description:
 * @Author: luohf
 * @Email:496349136@qq.com
 * @CreateDate: 2019/5/4 9:17
 * @UpdateUser:
 * @UpdateDate:
 * @UpdateRemark:
 */
public class CallView extends FrameLayout {
    private SurfaceView localSurfaceView;
    private SurfaceView remoteSurfaceView;

    public CallView(Context context) {
        this(context,null);
    }

    public CallView(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public CallView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr,0);
    }

    public CallView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        //初始化
        localSurfaceView = new SurfaceView(context);
        remoteSurfaceView = new SurfaceView(context);
    }

}
