package com.example.appblocker

import android.os.StrictMode
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

    // ⚠️  This must be a GOOGLE APP PASSWORD (16 chars), NOT your normal Gmail password.
    // Normal passwords are blocked by Gmail for SMTP. Generate one at:
    // Google Account → Security → 2-Step Verification → App Passwords
    private const val SENDER_EMAIL   = "appblocker05@gmail.com"
    private const val APP_PASSWORD    = "bmsufyhnfktpfthf"  // Google App Password (16 chars)
    private const val RECEIVER_EMAIL  = "riddhimjainsandeep@gmail.com"

    fun sendReport(subject: String, body: String, onResult: (success: Boolean, error: String?) -> Unit) {
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
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(RECEIVER_EMAIL))
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
