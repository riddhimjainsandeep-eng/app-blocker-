package com.example.appblocker

import android.os.StrictMode
import com.example.appblocker.BuildConfig
import java.util.Properties
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Sends an email via Gmail SMTP on a background thread.
 * Uses the dedicated appblocker05@gmail.com account.
 *
 * NOTE: For this to work you must:
 * 1. Enable "Less secure app access" in the sender Gmail account settings, OR
 * 2. Use an App Password (recommended): Google Account → Security → App Passwords
 */
object EmailSender {

    private const val SENDER_EMAIL   = "appblocker05@gmail.com"
    private val APP_PASSWORD         = BuildConfig.GMAIL_APP_PASSWORD

    fun sendReport(receiverEmail: String, subject: String, body: String, onResult: (success: Boolean, error: String?) -> Unit) {
        android.util.Log.d("SecurityAudit", "EmailSender invoked.")
        // Must run on a background thread — network operations crash on main thread
        Thread {
            try {
                // Allow network on main-ish thread during this call (needed for older minSdk)
                val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)

                val props = Properties().apply {
                    put("mail.smtp.host",            "smtp.gmail.com")
                    put("mail.smtp.port",            "587")
                    put("mail.smtp.auth",            "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.ssl.protocols",   "TLSv1.2")
                }

                val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD)
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(SENDER_EMAIL, "Study Sanctum"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(receiverEmail))
                    setSubject(subject)
                    setContent(body, "text/html; charset=utf-8")   // ← HTML format
                }

                Transport.send(message)
                onResult(true, null)

            } catch (e: MessagingException) {
                onResult(false, e.message)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }.start()
    }
}
