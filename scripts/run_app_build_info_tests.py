#!/usr/bin/env python3
"""Check installed-package About data without a device or a hardcoded library BuildConfig."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = Path(os.environ['JAVA_HOME']) / 'bin'
fixtures = {
    'android/os/Build.java': '''package android.os; public class Build {
        public static class VERSION { public static int SDK_INT=35; }
    }''',
    'android/os/Bundle.java': '''package android.os; public class Bundle extends java.util.HashMap<String,Object> {
        public String getString(String key){Object value=get(key);return value instanceof String?(String)value:null;}
    }''',
    'android/content/pm/ApplicationInfo.java': '''package android.content.pm; public class ApplicationInfo {
        public android.os.Bundle metaData;
    }''',
    'android/content/pm/PackageInfo.java': '''package android.content.pm; public class PackageInfo {
        public String versionName; public int versionCode; public long longVersionCode;
        public ApplicationInfo applicationInfo; public long getLongVersionCode(){return longVersionCode;}
    }''',
    'android/content/pm/PackageManager.java': '''package android.content.pm; public abstract class PackageManager {
        public static final int GET_META_DATA=128;
        public abstract PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException;
        public static class NameNotFoundException extends Exception {}
    }''',
    'android/content/Context.java': '''package android.content; public abstract class Context {
        public abstract String getPackageName();
        public abstract android.content.pm.PackageManager getPackageManager();
        public abstract String getString(int id,Object... values);
    }''',
    'com/mooncast/locale/R.java': '''package com.mooncast.locale; public class R {
        public static class string {public static final int about_version_name=1,about_version_code=2,
            about_package_name=3,about_build_id=4,about_unavailable=5;}
    }''',
    'com/mooncast/locale/AppBuildInfoTest.java': '''package com.mooncast.locale;
        import android.content.*; import android.content.pm.*; import android.os.*;
        public class AppBuildInfoTest {
            static PackageInfo installed=new PackageInfo(); static boolean missing;
            static final Context context=new Context(){
                public String getPackageName(){return "com.mooncast.example";}
                public PackageManager getPackageManager(){return new PackageManager(){
                    public PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException {
                        if(!name.equals(getPackageName())||flags!=GET_META_DATA)throw new AssertionError("read own installed package with metadata");
                        if(missing)throw new NameNotFoundException();return installed;
                    }
                };}
                public String getString(int id,Object... values){String[] formats={"","Version: %s","Code: %s","Package: %s","Build: %s","Unavailable"};return String.format(formats[id],values);}
            };
            static void check(boolean good,String reason){if(!good)throw new AssertionError(reason);}
            public static void main(String[] args){
                installed.versionName="0.6.0";installed.versionCode=7;installed.longVersionCode=4294967303L;
                String description=AppBuildInfo.describe(context);
                check(description.contains("Version: 0.6.0")&&description.contains("Code: 4294967303"),"use installed version and long code");
                check(description.contains("Package: com.mooncast.example")&&!description.contains("Build:"),"missing build ID stays hidden");
                installed.applicationInfo=new ApplicationInfo();installed.applicationInfo.metaData=new Bundle();
                installed.applicationInfo.metaData.put(AppBuildInfo.BUILD_ID_METADATA,"  abc1234  ");
                check(AppBuildInfo.describe(context).contains("Build: abc1234"),"provided build ID displayed");
                installed.applicationInfo.metaData.put(AppBuildInfo.BUILD_ID_METADATA," ");
                check(!AppBuildInfo.describe(context).contains("Build:"),"empty build ID stays hidden");
                installed.applicationInfo.metaData.put(AppBuildInfo.BUILD_ID_METADATA,1234);
                check(!AppBuildInfo.describe(context).contains("Build:"),"non-string metadata cannot crash About");
                Build.VERSION.SDK_INT=26;
                check(AppBuildInfo.describe(context).contains("Code: 7"),"Android 26 uses legacy versionCode");
                installed.versionName=null;
                check(AppBuildInfo.describe(context).contains("Version: Unavailable"),"missing name is explicit");
                missing=true;description=AppBuildInfo.describe(context);
                check(description.contains("Version: Unavailable")&&description.contains("Code: Unavailable")&&description.contains("com.mooncast.example"),"package lookup failure is explicit");
                System.out.println("PASS: installed version, long/legacy code, optional build ID and unavailable metadata");
            }
        }
    ''',
}
with tempfile.TemporaryDirectory(prefix='mooncast-about-') as directory:
    work = Path(directory)
    sources = [root / 'locale-core/src/main/java/com/mooncast/locale/AppBuildInfo.java']
    for relative, content in fixtures.items():
        path = work / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(path)
    subprocess.run([java / 'javac', '--release', '17', '-encoding', 'UTF-8', '-d', work, *sources], check=True)
    subprocess.run([java / 'java', '-cp', work, 'com.mooncast.locale.AppBuildInfoTest'], check=True)
