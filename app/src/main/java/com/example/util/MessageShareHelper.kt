package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.Appointment
import com.example.data.model.SalonCatalog

object MessageShareHelper {

    /**
     * Pre-filled message template sent BY the customer TO Salon Abo Al-Ata on WhatsApp
     * to confirm their reservation details.
     */
    fun formatSalonConfirmationTemplate(
        appointment: Appointment,
        salonPhone: String = SalonCatalog.DEFAULT_SALON_PHONE,
    ): String {
        val haircutLine = if (appointment.selectedHaircut.isNotBlank()) {
            "\n💈 القصة المطلوبة: ${appointment.selectedHaircut}"
        } else {
            ""
        }
        val notesLine = if (appointment.notes.isNotBlank()) {
            "\n📝 ملاحظات: ${appointment.notes}"
        } else {
            ""
        }
        return buildString {
            append("السلام عليكم *صالون أبو العطا* ✂️\n")
            append("أرغب في تأكيد حجز الموعد بالتفاصيل التالية:\n\n")
            append("👤 الاسم: *${appointment.customerName}*\n")
            append("📱 رقم الهاتف: ${appointment.customerPhone}\n")
            append("🧔 الصنايعي: *${appointment.barberName}*\n")
            append("✨ الباكيدج: *${appointment.packageTitle}*\n")
            append("🔹 الخدمات: ${appointment.packageServices}")
            append(haircutLine)
            append("\n📅 اليوم: *${appointment.appointmentDate}*\n")
            append("⏰ الساعة: *${appointment.appointmentTime}*")
            append(notesLine)
            append("\n\n📞 تليفون المحل: ${SalonCatalog.SHOP_DIRECT_PHONE} | الحجز: $salonPhone")
        }
    }

    /**
     * General appointment summary message (suitable for customer reminders, SMS, or sharing).
     */
    fun formatAppointmentMessage(
        appointment: Appointment,
        salonPhone: String = SalonCatalog.DEFAULT_SALON_PHONE,
    ): String {
        val haircutLine = if (appointment.selectedHaircut.isNotBlank()) {
            "\n💈 القصة المختارة: ${appointment.selectedHaircut}"
        } else {
            ""
        }
        val notesLine = if (appointment.notes.isNotBlank()) {
            "\n📝 ملاحظات: ${appointment.notes}"
        } else {
            ""
        }
        return buildString {
            append("✂️ *صالون أبو العطا للحلاقة الرجالية* ✂️\n")
            append("مرحباً *${appointment.customerName}*، تم تأكيد موعدك بنجاح!\n\n")
            append("📅 اليوم: ${appointment.appointmentDate}\n")
            append("⏰ الساعة: ${appointment.appointmentTime}\n")
            append("🧔 الصنايعي: ${appointment.barberName}\n")
            append("✨ الباكيدج: ${appointment.packageTitle}\n")
            append("🔹 يشمل: ${appointment.packageServices}")
            append(haircutLine)
            append(notesLine)
            append("\n📱 رقم الزبون: ${appointment.customerPhone}\n")
            append("🔔 سيصلك تنبيه قبل الميعاد بنصف ساعة.\n")
            append("📞 رقم المحل: ${SalonCatalog.SHOP_DIRECT_PHONE} | الحجز والواتساب: $salonPhone")
        }
    }

    /**
     * Normalizes Egyptian local phone numbers (e.g. 01029898309 -> 201029898309) for WhatsApp links.
     */
    fun normalizePhoneForWhatsApp(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("01") && digits.length in 10..11 -> "2$digits"
            digits.startsWith("1") && digits.length in 9..10 -> "20$digits"
            else -> digits
        }
    }

    /**
     * Builds a universal wa.me HTTPS Intent pre-filled with the target phone and message.
     */
    fun buildWhatsAppWebIntent(targetPhone: String, message: String): Intent {
        val normalized = normalizePhoneForWhatsApp(targetPhone)
        val encodedText = Uri.encode(message)
        val uri = Uri.parse("https://wa.me/$normalized?text=$encodedText")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches WhatsApp directly to the salon's phone number with a pre-filled reservation
     * confirmation template so the customer can confirm their appointment details in one tap.
     */
    fun launchWhatsAppSalonConfirmation(
        context: Context,
        appointment: Appointment,
        salonPhone: String = SalonCatalog.DEFAULT_SALON_PHONE,
    ) {
        val targetSalon = salonPhone.ifBlank { SalonCatalog.DEFAULT_SALON_PHONE }
        val template = formatSalonConfirmationTemplate(appointment, targetSalon)
        sendWhatsAppMessage(
            context = context,
            targetPhone = targetSalon,
            message = template,
        )
    }

    fun sendWhatsAppMessage(context: Context, targetPhone: String, message: String) {
        val normalized = normalizePhoneForWhatsApp(targetPhone)
        val encodedText = Uri.encode(message)

        // Try native whatsapp:// deep link first if WhatsApp is installed, then fall back to https://wa.me/
        val nativeUri = Uri.parse("whatsapp://send?phone=$normalized&text=$encodedText")
        val nativeIntent = Intent(Intent.ACTION_VIEW, nativeUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (nativeIntent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(nativeIntent)
                return
            } catch (_: Exception) {
                // Fall through to universal wa.me link
            }
        }

        val webIntent = buildWhatsAppWebIntent(targetPhone, message)
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح واتساب على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendSmsMessage(context: Context, targetPhone: String, message: String) {
        val cleanPhone = targetPhone.filter { it.isDigit() || it == '+' }
        val uri = Uri.parse("smsto:$cleanPhone")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيق الرسائل على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    fun dialSalon(context: Context, salonPhone: String = SalonCatalog.DEFAULT_SALON_PHONE) {
        dialPhone(context, salonPhone)
    }

    fun dialPhone(context: Context, targetPhone: String) {
        val cleanPhone = targetPhone.filter { it.isDigit() || it == '+' }
        val uri = Uri.parse("tel:$cleanPhone")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيق الاتصال", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendWhatsAppToCustomer(context: Context, appointment: Appointment) {
        val message = formatAppointmentMessage(appointment)
        sendWhatsAppMessage(
            context = context,
            targetPhone = appointment.customerPhone,
            message = message,
        )
    }
}
