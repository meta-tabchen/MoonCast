package com.mooncast.locale;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Installed application identity, independent of a phone/TV library's BuildConfig. */
public final class AppBuildInfo {
    public static final String BUILD_ID_METADATA = "com.mooncast.BUILD_ID";
    private AppBuildInfo() {}

    @SuppressWarnings("deprecation")
    public static String describe(Context context) {
        String packageName = context.getPackageName();
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            String name = info.versionName;
            if (name == null || name.trim().isEmpty()) name = context.getString(R.string.about_unavailable);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            String details = context.getString(R.string.about_version_name, name)
                + "\n" + context.getString(R.string.about_version_code, Long.toString(code))
                + "\n" + context.getString(R.string.about_package_name, packageName);
            if (info.applicationInfo != null && info.applicationInfo.metaData != null) {
                String buildId = info.applicationInfo.metaData.getString(BUILD_ID_METADATA);
                if (buildId != null && !buildId.trim().isEmpty())
                    details += "\n" + context.getString(R.string.about_build_id, buildId.trim());
            }
            return details;
        } catch (PackageManager.NameNotFoundException missing) {
            return context.getString(R.string.about_version_name, context.getString(R.string.about_unavailable))
                + "\n" + context.getString(R.string.about_version_code, context.getString(R.string.about_unavailable))
                + "\n" + context.getString(R.string.about_package_name, packageName);
        }
    }
}
