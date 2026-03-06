package com.example.appblocker

import android.content.Context
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * A single Worker handles DAILY, WEEKLY, and MONTHLY reports.
 * The report type is passed as an input data string.
 */
class WeeklyReportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val type = inputData.getString(KEY_REPORT_TYPE) ?: TYPE_DAILY
        return try {
            val (subject, body) = buildReport(applicationContext, type)
            val userEmail = applicationContext.getSharedPreferences(SetupActivity.PREFS_SETUP, Context.MODE_PRIVATE)
                .getString(SetupActivity.KEY_USER_EMAIL, "riddhimjainsandeep@gmail.com") ?: "riddhimjainsandeep@gmail.com"

            var success = false
            val latch = java.util.concurrent.CountDownLatch(1)
            EmailSender.sendReport(userEmail, subject, body) { ok, _ ->
                success = ok
                latch.countDown()
            }
            latch.await(30, TimeUnit.SECONDS)

            // For monthly report: reschedule for the last day of next month
            if (type == TYPE_MONTHLY) {
                scheduleMonthlyReport(applicationContext)
            }

            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_TAG_DAILY   = "focus_report_daily"
        const val WORK_TAG_WEEKLY  = "focus_report_weekly"
        const val WORK_TAG_MONTHLY = "focus_report_monthly"
        const val KEY_REPORT_TYPE  = "report_type"
        const val TYPE_DAILY       = "DAILY"
        const val TYPE_WEEKLY      = "WEEKLY"
        const val TYPE_MONTHLY     = "MONTHLY"

        // ─── Schedule all three jobs ───────────────────────────────────────

        fun scheduleAll(context: Context) {
            scheduleDailyReport(context)
            scheduleWeeklyReport(context)
            scheduleMonthlyReport(context)
        }

        /** Runs every 24 h */
        private fun scheduleDailyReport(context: Context) {
            val data = Data.Builder().putString(KEY_REPORT_TYPE, TYPE_DAILY).build()
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(1, TimeUnit.DAYS)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG_DAILY)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG_DAILY, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** Runs every 7 days, with initial delay to next Sunday 8 AM */
        private fun scheduleWeeklyReport(context: Context) {
            val data = Data.Builder().putString(KEY_REPORT_TYPE, TYPE_WEEKLY).build()
            val delay = millisToNextSunday8AM()
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG_WEEKLY)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG_WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** One-time job to last day of current month — reschedules itself after running */
        fun scheduleMonthlyReport(context: Context) {
            val data = Data.Builder().putString(KEY_REPORT_TYPE, TYPE_MONTHLY).build()
            val delay = millisToLastDayOfMonth()
            val request = OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG_MONTHLY)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_TAG_MONTHLY, ExistingWorkPolicy.KEEP, request
            )
        }

        /** Milliseconds until next Sunday at 08:00 AM */
        private fun millisToNextSunday8AM(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysUntilSunday = (Calendar.SUNDAY - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
            target.add(Calendar.DAY_OF_YEAR, if (daysUntilSunday == 0) 7 else daysUntilSunday)
            return maxOf(target.timeInMillis - now.timeInMillis, 1000L)
        }

        /** Milliseconds until last day of current month at 08:00 AM */
        private fun millisToLastDayOfMonth(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If today IS the last day (already past 8 AM), schedule for next month's last day
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.MONTH, 1)
                target.set(Calendar.DAY_OF_MONTH, target.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            return target.timeInMillis - now.timeInMillis
        }

        // ─── Build the HTML report based on type ──────────────────────────

        fun buildReport(context: Context, type: String): Pair<String, String> {
            val stats = context.getSharedPreferences("BlockerStats", Context.MODE_PRIVATE)
            val prefs = context.getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)

            val totalEver    = stats.getInt("total_blocks", 0)
            val thisWeek     = stats.getInt("weekly_blocks", 0)
            val lastWeek     = stats.getInt("last_week_total", 0)
            val blockedApps  = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
            val blockedSites = prefs.getStringSet("blocked_websites", emptySet()) ?: emptySet()

            val now     = Calendar.getInstance()
            val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(now.time)
            val pm      = try { context.packageManager } catch (e: Exception) { null }

            // Time window for "today" vs "this week" vs "this month"
            val windowMs = when (type) {
                TYPE_DAILY   -> 24L * 60 * 60 * 1000
                TYPE_WEEKLY  -> 7L  * 24 * 60 * 60 * 1000
                TYPE_MONTHLY -> 31L * 24 * 60 * 60 * 1000
                else         -> 24L * 60 * 60 * 1000
            }
            val windowStart = now.timeInMillis - windowMs
            val timestamps  = (stats.getString("block_timestamps", "") ?: "")
                .split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
            val periodBlocks = timestamps.count { it > windowStart }

            // Peak hour in window
            val hourCounts = IntArray(24)
            timestamps.filter { it > windowStart }.forEach { ts ->
                val cal = Calendar.getInstance().apply { timeInMillis = ts }
                hourCounts[cal.get(Calendar.HOUR_OF_DAY)]++
            }
            val peakHour   = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0
            val peakLabel  = String.format("%02d:00 – %02d:00", peakHour, (peakHour + 1) % 24)

            // Per-app rows
            val appRows = blockedApps.joinToString("") { pkg ->
                val key   = "app_count_${pkg.replace('.', '_')}"
                val count = stats.getInt(key, 0)
                val name  = try { pm?.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                """<tr>
                  <td style="padding:8px 12px;border-bottom:1px solid #2A2A4A;color:#CCCCDD;">$name</td>
                  <td style="padding:8px 12px;border-bottom:1px solid #2A2A4A;color:#C8A2F8;text-align:center;font-weight:bold;">$count</td>
                </tr>"""
            }

            val (title, periodLabel, trendNote) = when (type) {
                TYPE_DAILY   -> Triple(
                    "Daily Focus Report",
                    "Today",
                    if (periodBlocks == 0) "🏆 Clean day — zero distractions!" else "📊 $periodBlocks attempt(s) today."
                )
                TYPE_WEEKLY  -> Triple(
                    "Weekly Focus Report",
                    "This Week",
                    when {
                        periodBlocks < lastWeek -> "📉 Improved! ${lastWeek - periodBlocks} fewer than last week."
                        periodBlocks > lastWeek -> "📈 +${periodBlocks - lastWeek} more than last week. Keep fighting."
                        else -> "➡️ Same as last week ($periodBlocks attempts)."
                    }
                )
                else -> Triple(
                    "Monthly Focus Report",
                    "This Month",
                    if (periodBlocks == 0) "🏆 Outstanding month — you stayed focused!" else "📊 $periodBlocks total attempts this month."
                )
            }

            val coachNote = when {
                periodBlocks == 0            -> "Perfect period! Every second you spent studying instead of scrolling compounds into your CA/ISC result."
                periodBlocks < 5             -> "Very strong control. The habit is forming — keep showing up."
                periodBlocks in 5..15        -> "$periodBlocks attempts. Try phone face-down + a physical Pomodoro timer during study blocks."
                periodBlocks in 16..40       -> "High attempt count. Consider Grayscale mode on your phone to make apps less appealing."
                else                         -> "$periodBlocks attempts this period. Time to go harder — delete the apps, not just block them."
            }

            val userName = context.getSharedPreferences(SetupActivity.PREFS_SETUP, Context.MODE_PRIVATE)
                .getString(SetupActivity.KEY_USER_NAME, "Scholar") ?: "Scholar"

            val subject = "📊 Study Sanctum $title — $dateStr"
            val html = buildHtml(userName, title, dateStr, periodLabel, periodBlocks, trendNote,
                thisWeek, totalEver, blockedApps.size, blockedSites.size,
                appRows, peakLabel, coachNote)

            return Pair(subject, html)
        }

        private fun buildHtml(
            userName: String,
            title: String, dateStr: String, periodLabel: String,
            periodBlocks: Int, trendNote: String,
            thisWeek: Int, totalEver: Int,
            appCount: Int, siteCount: Int,
            appRows: String, peakLabel: String, coachNote: String
        ) = """
<!DOCTYPE html><html><head><meta charset="UTF-8"/>
<style>
  body{margin:0;padding:0;background:#0D0D1A;font-family:Arial,sans-serif;color:#CCCCDD;}
  .wrap{max-width:600px;margin:0 auto;padding:32px 20px;}
  .header{text-align:center;margin-bottom:28px;}
  .logo{font-size:26px;font-weight:bold;color:#C8A2F8;letter-spacing:1px;}
  .sub{font-size:12px;color:#6666AA;margin-top:4px;}
  .card{background:#12122A;border:1px solid #2A2A4A;border-radius:12px;padding:20px;margin-bottom:16px;}
  .ct{font-size:10px;letter-spacing:2px;color:#6666AA;text-transform:uppercase;margin-bottom:14px;}
  .row{display:flex;justify-content:space-between;margin-bottom:10px;font-size:14px;}
  .lbl{color:#8888AA;} .val{color:#FFF;font-weight:bold;}
  .trend{background:#1A1A2E;border-radius:8px;padding:10px 14px;margin-top:10px;font-size:13px;color:#C8A2F8;}
  table{width:100%;border-collapse:collapse;}
  th{text-align:left;padding:8px 12px;color:#6666AA;font-size:10px;letter-spacing:1px;text-transform:uppercase;border-bottom:1px solid #2A2A4A;}
  .coach{background:#0A1A0A;border:1px solid #2A4A2A;border-radius:12px;padding:18px 20px;font-size:13px;line-height:1.8;color:#AADDAA;}
  .footer{text-align:center;margin-top:28px;font-size:10px;color:#444466;}
</style></head><body>
<div class="wrap">
  <div class="header">
    <div class="logo">📚 Study Sanctum</div>
    <div class="sub" style="font-size:18px; color:#FFF; margin:16px 0 8px;">Hi, $userName!</div>
    <div class="sub">$title &nbsp;·&nbsp; $dateStr</div>
  </div>
  <div class="card">
    <div class="ct">$periodLabel Overview</div>
    <div class="row"><span class="lbl">Block attempts ($periodLabel)</span><span class="val">$periodBlocks</span></div>
    <div class="row"><span class="lbl">This week's total</span><span class="val">$thisWeek</span></div>
    <div class="row"><span class="lbl">All-time total</span><span class="val">$totalEver</span></div>
    <div class="row"><span class="lbl">Apps blocked</span><span class="val">$appCount</span></div>
    <div class="row"><span class="lbl">Websites blocked</span><span class="val">$siteCount</span></div>
    <div class="trend">$trendNote</div>
  </div>
  <div class="card">
    <div class="ct">App Breakdown (All-time)</div>
    <table>
      <tr><th>App</th><th style="text-align:center;">Total Blocks</th></tr>
      ${if (appRows.isEmpty()) "<tr><td colspan='2' style='padding:12px;color:#555577;'>No attempts recorded yet.</td></tr>" else appRows}
    </table>
  </div>
  <div class="card">
    <div class="ct">⏰ Peak Distraction Window</div>
    <div class="row"><span class="lbl">Most attempts around</span><span class="val" style="color:#F8D7A2;">$peakLabel</span></div>
    <p style="font-size:12px;color:#6666AA;margin:4px 0 0;">Try Do Not Disturb during this window.</p>
  </div>
  <div class="coach">💡 <strong>Coach's Note</strong><br/><br/>$coachNote</div>
  <div class="footer">Sent automatically by Study Sanctum Focus Guard</div>
</div></body></html>
        """.trimIndent()
    }
}
