package com.robining.webrtcdemo;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Author: luohf
 * @Email:496349136@qq.com
 * @CreateDate: 2019/4/30 13:53
 * @UpdateUser:
 * @UpdateDate:
 * @UpdateRemark:
 */
public class PermissionUtil {
    public static List<String> getManifestShouldRequestPermissions(Activity activity) {
        ArrayList<String> dangerousPermissions = new ArrayList<>();

        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] requested = packageInfo.requestedPermissions;
            for (String str : requested) {
                try {
                    PermissionInfo info = pm.getPermissionInfo(str, PackageManager.GET_META_DATA);
                    boolean isDangerous = info.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS;
                    isDangerous = true;
                    if (isDangerous) {
                        dangerousPermissions.add(str);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {

                }
            }
        } catch (PackageManager.NameNotFoundException var10) {
            var10.printStackTrace();
        }

        return dangerousPermissions;
    }
}
