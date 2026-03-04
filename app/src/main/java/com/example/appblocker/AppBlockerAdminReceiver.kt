package com.example.appblocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin Receiver.
 * Once the user activates this, the app cannot be uninstalled
 * without going to Settings → Security → Device Admins → Deactivate first.
 */
class AppBlockerAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "🔒 Focus Guard is now protected from uninstall.", Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "⚠️ Disabling this will let you uninstall Focus Guard and lose your blocking protection. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device admin deactivated. You can now uninstall.", Toast.LENGTH_LONG).show()
    }
}
