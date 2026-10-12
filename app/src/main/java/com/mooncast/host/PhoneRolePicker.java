package com.mooncast.host;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.Consumer;

/** Keep the sender Activity and its capture service until a different mode is confirmed. */
final class PhoneRolePicker {
    private PhoneRolePicker() {}

    static void show(Activity activity, Consumer<String> accepted) {
        String[] labels = {activity.getString(R.string.phone_role_auto),
            activity.getString(R.string.phone_role_phone), activity.getString(R.string.phone_role_tv)};
        new AlertDialog.Builder(activity).setTitle(R.string.phone_role_title)
            .setSingleChoiceItems(labels, 1, (dialog, index) -> {
                dialog.dismiss();
                // Phone is the current running role, including automatic device detection.
                if (index == 1) return;
                String choice = index == 2 ? "tv" : "auto";
                new AlertDialog.Builder(activity).setTitle(R.string.phone_role_title)
                    .setMessage(R.string.phone_role_stop_notice)
                    .setPositiveButton(android.R.string.ok, (confirm, which) -> accepted.accept(choice))
                    .setNegativeButton(android.R.string.cancel, null).show();
            }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
