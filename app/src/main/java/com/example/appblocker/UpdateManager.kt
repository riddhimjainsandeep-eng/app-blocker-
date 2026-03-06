package com.example.appblocker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * OTA Update Manager — checks GitHub for a newer version.json,
 * downloads the APK via DownloadManager, validates it, then triggers the system installer.
 *
 * FIX LOG:
 *  - Added DownloadManager STATUS check before install (catches network/404 errors)
 *  - Added minimum file size check (APK must be >500 KB — rejects HTML error pages)
 *  - Version code now read from BuildConfig dynamically (no more hardcoded constant)
 */
object UpdateManager {

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/riddhimjainsandeep-eng/app-blocker-/main/version.json"

    // Minimum APK size — any file smaller than this is an error page, not an APK
    private const val MIN_APK_BYTES = 500L * 1024 // 500 KB

    fun checkAndUpdate(context: Context) {
        Toast.makeText(context, "🔍 Checking for updates...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val json = URL(VERSION_JSON_URL).readText()
                val obj  = JSONObject(json)
                val latestCode = obj.getInt("versionCode")
                val latestName = obj.getString("versionName")
                val apkUrl     = obj.getString("apkUrl")
                val notes      = obj.optString("releaseNotes", "")

                // Get CURRENT version code from the installed package (always accurate)
                val currentCode = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                    }
                } catch (e: Exception) { 1 }

                val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return@Thread

                activity.runOnUiThread {
                    if (latestCode > currentCode) {
                        AlertDialog.Builder(activity, R.style.BlockerDialog)
                            .setTitle("🚀 Update Available — v$latestName")
                            .setMessage("$notes\n\nYour version: $currentCode → Latest: $latestCode\n\nDownload and install now?")
                            .setPositiveButton("Install Update") { _, _ ->
                                downloadAndInstall(activity, apkUrl, "StudySanctum_v${latestName}.apk")
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        AlertDialog.Builder(activity, R.style.BlockerDialog)
                            .setTitle("✅ You're up to date!")
                            .setMessage("Study Sanctum v${latestName} is the latest version. (Your code: $currentCode)")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return@Thread
                activity.runOnUiThread {
                    AlertDialog.Builder(activity, R.style.BlockerDialog)
                        .setTitle("⚠️ Check Failed")
                        .setMessage("Could not reach update server.\n\n${e.javaClass.simpleName}: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun downloadAndInstall(context: Context, apkUrl: String, fileName: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Delete old cached APK if it exists
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Study Sanctum Update")
            setDescription("Downloading update...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
            // Force fresh download, no cached redirect
            addRequestHeader("Cache-Control", "no-cache")
        }

        val downloadId = dm.enqueue(request)
        Toast.makeText(context, "⬇️ Downloading update...", Toast.LENGTH_LONG).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                context.unregisterReceiver(this)

                // ── Step 1: Check DownloadManager status ──────────────────────
                val query  = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                var dmStatus = DownloadManager.STATUS_FAILED
                var dmReason = 0
                if (cursor.moveToFirst()) {
                    dmStatus = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    dmReason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
                cursor.close()

                val activity = context as? androidx.appcompat.app.AppCompatActivity
                if (dmStatus != DownloadManager.STATUS_SUCCESSFUL) {
                    activity?.runOnUiThread {
                        AlertDialog.Builder(activity, R.style.BlockerDialog)
                            .setTitle("❌ Download Failed")
                            .setMessage("The update could not be downloaded.\n\nError code: $dmReason\n\nMake sure the GitHub Release exists and the APK has been uploaded.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return
                }

                // ── Step 2: Validate file size (must be a real APK, not an error page) ──
                val apkFile = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) {
                    apkFile.delete() // clean up the garbage file
                    activity?.runOnUiThread {
                        AlertDialog.Builder(activity, R.style.BlockerDialog)
                            .setTitle("❌ Invalid File Downloaded")
                            .setMessage("The downloaded file is too small to be a valid APK (${apkFile.length()} bytes).\n\nThis usually means the GitHub Release has not been published yet or the APK file name in the release is different.\n\nExpected file: $fileName")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return
                }

                // ── Step 3: All good — trigger installer ──────────────────────
                activity?.runOnUiThread { installApk(context, apkFile) }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
