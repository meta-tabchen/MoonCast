#!/usr/bin/env python3
"""Exercise actual phone/TV picker callbacks with a minimal dialog fixture, without a device.

This checks the teardown boundary, not Android Activity lifecycle or TV interoperability.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = Path(os.environ['JAVA_HOME']) / 'bin'
fixtures = {
    'android/R.java': '''package android; public final class R {
        public static final class string { public static final int ok=1,cancel=2; }
    }''',
    'android/content/DialogInterface.java': '''package android.content;
        public interface DialogInterface { void dismiss();
        interface OnClickListener { void onClick(DialogInterface dialog,int which); } }
    ''',
    'android/app/Activity.java': '''package android.app; public class Activity {
        public String getString(int id) { return Integer.toString(id); }
    }''',
    'android/app/AlertDialog.java': '''package android.app;
        import android.content.DialogInterface; import java.util.ArrayList;
        public class AlertDialog implements DialogInterface {
            public static final ArrayList<AlertDialog> shown=new ArrayList<>();
            public DialogInterface.OnClickListener selected,positive,negative;
            public int checked=-1; public boolean dismissed;
            public void dismiss(){dismissed=true;}
            public void choose(int index){selected.onClick(this,index);}
            public void confirm(){if(positive!=null)positive.onClick(this,1);dismiss();}
            public void cancel(){if(negative!=null)negative.onClick(this,2);dismiss();}
            public void back(){dismiss();}
            public static final class Builder {
                private final AlertDialog dialog=new AlertDialog();
                public Builder(Activity activity){}
                public Builder setTitle(int title){return this;}
                public Builder setMessage(int message){return this;}
                public Builder setSingleChoiceItems(String[] labels,int checked,DialogInterface.OnClickListener selected){dialog.checked=checked;dialog.selected=selected;return this;}
                public Builder setPositiveButton(int label,DialogInterface.OnClickListener listener){dialog.positive=listener;return this;}
                public Builder setNegativeButton(int label,DialogInterface.OnClickListener listener){dialog.negative=listener;return this;}
                public AlertDialog show(){shown.add(dialog);return dialog;}
            }
        }
    ''',
    'com/mooncast/tv/R.java': '''package com.mooncast.tv; public final class R {
        public static final class string {
            public static final int tv_device_mode=1,tv_role_auto=2,tv_role_phone=3,tv_role_tv=4,tv_role_stop_notice=5;
        }
    }''',
    'com/mooncast/host/R.java': '''package com.mooncast.host; public final class R {
        public static final class string {
            public static final int phone_role_title=1,phone_role_auto=2,phone_role_phone=3,phone_role_tv=4,phone_role_stop_notice=5;
        }
    }''',
    'com/mooncast/host/PhoneRolePickerTest.java': '''package com.mooncast.host;
        import android.app.Activity; import android.app.AlertDialog; import java.util.ArrayList;
        public final class PhoneRolePickerTest {
            private static final ArrayList<String> accepted=new ArrayList<>();
            private static void check(boolean good,String reason){if(!good)throw new AssertionError(reason);}
            private static AlertDialog open(){AlertDialog.shown.clear();PhoneRolePicker.show(new Activity(),accepted::add);return AlertDialog.shown.get(0);}
            public static void main(String[] args){
                AlertDialog initial=open();
                check(accepted.isEmpty()&&initial.checked==1,"opening preserves capture and selects running phone role");
                initial.cancel();check(accepted.isEmpty(),"cancel must not stop capture or recreate homepage");
                open().back();check(accepted.isEmpty(),"Back must not stop capture or recreate homepage");
                open().choose(1);check(accepted.isEmpty()&&AlertDialog.shown.size()==1,"same phone closes without a transaction");
                open().choose(2);check(accepted.isEmpty()&&AlertDialog.shown.size()==2,"TV requires confirmation before cleanup");
                AlertDialog.shown.get(1).cancel();check(accepted.isEmpty(),"cancel confirmation preserves sender");
                open().choose(2);AlertDialog.shown.get(1).back();check(accepted.isEmpty(),"Back on confirmation preserves sender");
                open().choose(2);AlertDialog.shown.get(1).confirm();check(accepted.size()==1&&accepted.get(0).equals("tv"),"confirmed TV emits chosen role once");
                open().choose(0);check(accepted.size()==1,"Automatic also waits for confirmation");
                AlertDialog.shown.get(1).confirm();check(accepted.size()==2&&accepted.get(1).equals("auto"),"Automatic preserves original policy option");
                System.out.println("PASS: phone role menu open, cancel, Back, same role and confirmation teardown boundary");
            }
        }
    ''',
    'com/mooncast/tv/TvRolePickerTest.java': '''package com.mooncast.tv;
        import android.app.Activity; import android.app.AlertDialog; import java.util.ArrayList;
        public final class TvRolePickerTest {
            private static final ArrayList<String> accepted=new ArrayList<>();
            private static void check(boolean good,String reason){if(!good)throw new AssertionError(reason);}
            private static AlertDialog open(){AlertDialog.shown.clear();TvRolePicker.show(new Activity(),accepted::add);return AlertDialog.shown.get(0);}
            public static void main(String[] args){
                AlertDialog initial=open();
                check(accepted.isEmpty()&&initial.checked==2,"opening preserves receiver and selects running TV role");
                initial.cancel();check(accepted.isEmpty(),"cancel must not stop receiver");
                open().back();check(accepted.isEmpty(),"Back must not stop receiver");
                open().choose(2);check(accepted.isEmpty()&&AlertDialog.shown.size()==1,"same TV closes without a transaction");
                open().choose(1);check(accepted.isEmpty()&&AlertDialog.shown.size()==2,"different role requires confirmation before cleanup");
                AlertDialog.shown.get(1).cancel();check(accepted.isEmpty(),"cancel confirmation preserves receiver");
                open().choose(1);AlertDialog.shown.get(1).back();check(accepted.isEmpty(),"Back on confirmation preserves receiver");
                open().choose(1);AlertDialog.shown.get(1).confirm();check(accepted.size()==1&&accepted.get(0).equals("phone"),"confirmed phone emits chosen role once");
                open().choose(0);check(accepted.size()==1,"Automatic also waits for confirmation");
                AlertDialog.shown.get(1).confirm();check(accepted.size()==2&&accepted.get(1).equals("auto"),"Automatic preserves original policy option");
                System.out.println("PASS: TV role menu open, cancel, Back, same role and confirmation teardown boundary");
            }
        }
    ''',
}
with tempfile.TemporaryDirectory(prefix='mooncast-tv-role-') as directory:
    work = Path(directory)
    sources = [root / 'tv/src/main/java/com/mooncast/tv/TvRolePicker.java',
               root / 'app/src/main/java/com/mooncast/host/PhoneRolePicker.java']
    for relative, content in fixtures.items():
        path = work / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(path)
    subprocess.run([java / 'javac', '--release', '17', '-encoding', 'UTF-8', '-d', work,
                    *sources], check=True)
    subprocess.run([java / 'java', '-cp', work, 'com.mooncast.tv.TvRolePickerTest'], check=True)
    subprocess.run([java / 'java', '-cp', work, 'com.mooncast.host.PhoneRolePickerTest'], check=True)
