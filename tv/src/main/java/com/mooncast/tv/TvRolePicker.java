package com.mooncast.tv;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.Consumer;

/** Keep the receiver visible until a different mode is explicitly confirmed. */
final class TvRolePicker {
    private TvRolePicker() {}

    static void show(Activity activity, Consumer<String> accepted) {
        String[] labels = {activity.getString(R.string.tv_role_auto),
            activity.getString(R.string.tv_role_phone), activity.getString(R.string.tv_role_tv)};
        new AlertDialog.Builder(activity).setTitle(R.string.tv_device_mode)
            .setSingleChoiceItems(labels, 2, (dialog, index) -> {
                dialog.dismiss();
                // TV is the current running role, including when selected automatically.
                if (index == 2) return;
                String choice = index == 1 ? "phone" : "auto";
                new AlertDialog.Builder(activity).setTitle(R.string.tv_device_mode)
                    .setMessage(R.string.tv_role_stop_notice)
                    .setPositiveButton(android.R.string.ok, (confirm, which) -> accepted.accept(choice))
                    .setNegativeButton(android.R.string.cancel, null).show();
            }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
