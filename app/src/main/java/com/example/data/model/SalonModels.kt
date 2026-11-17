package com.example.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Appointment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val barberId: String = "mody",
    val barberName: String = "مودي",
    val packageId: String = "",
    val packageTitle: String = "",
    val packageServices: String = "",
    val selectedHaircut: String = "",
    val appointmentDate: String = "",
    val appointmentTime: String = "",
    val appointmentEpochMillis: Long = 0L,
    val notes: String = "",
    val status: String = "confirmed",
    val reminderScheduled: Boolean = true,
    val rating: Long = 0L,
    val feedbackComment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SalonPreference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val savedCustomerName: String = "",
    val savedPhone: String = "",
    val barber2CustomName: String = "",
    val barber3CustomName: String = "",
    val salonWhatsappPhone: String = "01029898309",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class BarberProfile(
    val id: String,
    val defaultName: String,
    val role: String,
    val specialty: String,
    val badge: String,
    val isMaster: Boolean = false,
)

data class SalonPackage(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val badge: String,
    val services: List<String>,
    val isFeatured: Boolean = false,
) {
    val servicesSummary: String
        get() = services.joinToString(" • ")
}

data class HaircutStyle(
    val id: String,
    val englishName: String,
    val arabicName: String,
    val category: String,
    val faceShape: String,
    val stylingTip: String,
    val description: String,
    @param:DrawableRes val imageRes: Int,
) {
    val fullDisplayName: String
        get() = "$arabicName ($englishName)"
}

object SalonCatalog {
    const val DEFAULT_SALON_PHONE = "01029898309"
    const val SHOP_DIRECT_PHONE = "0102048447"
    const val SALON_OWNER_EMAIL = "fareselbialy1941@gmail.com"
    val SALON_OWNER_PHONES_E164: Set<String> = setOf("+201029898309", "+201020484470")

    fun isVerifiedSalonOwner(
        email: String?,
        isEmailVerified: Boolean,
        phoneE164: String?,
    ): Boolean {
        val matchesVerifiedEmail = isEmailVerified &&
            email?.trim()?.lowercase() == SALON_OWNER_EMAIL
        val matchesOwnerPhone = !phoneE164.isNullOrBlank() &&
            phoneE164.trim() in SALON_OWNER_PHONES_E164
        return matchesVerifiedEmail || matchesOwnerPhone
    }

    fun getBarbers(pref: SalonPreference?): List<BarberProfile> {
        val barber2Name = pref?.barber2CustomName?.trim()?.takeIf { it.isNotEmpty() } ?: "صنايعي 2"
        val barber3Name = pref?.barber3CustomName?.trim()?.takeIf { it.isNotEmpty() } ?: "صنايعي 3"
        return listOf(
            BarberProfile(
                id = "mody",
                defaultName = "مودي",
                role = "ماستر باربر • صنايعي أول",
                specialty = "أحدث التدريجات العالمية (Skin Fade)، الاستشوار الاحترافي، ونحت اللحية",
                badge = "صنايعي أول ⭐",
                isMaster = true,
            ),
            BarberProfile(
                id = "barber_2",
                defaultName = barber2Name,
                role = if (barber2Name == "صنايعي 2") "كرسي رقم 2 • بدون اسم حالياً" else "حلاق محترف • كرسي 2",
                specialty = "قصات عصرية وكلاسيك، استشوار، وعناية متكاملة بالشعر والدقن",
                badge = "متاح للحجز",
                isMaster = false,
            ),
            BarberProfile(
                id = "barber_3",
                defaultName = barber3Name,
                role = if (barber3Name == "صنايعي 3") "كرسي رقم 3 • بدون اسم حالياً" else "حلاق محترف • كرسي 3",
                specialty = "قصات شبابية، تنظيف بشرة بالبخار، وماسكات نضارة وترطيب",
                badge = "متاح للحجز",
                isMaster = false,
            ),
        )
    }

    val packages: List<SalonPackage> = listOf(
        SalonPackage(
            id = "vip_royal",
            title = "باكيدج أبو العطا الملكي (VIP)",
            subtitle = "تجربة العناية الكاملة للشعر والدقن والبشرة",
            durationLabel = "60 دقيقة",
            badge = "الأكثر طلباً",
            services = listOf(
                "قصة شعر وتدريج احترافي حسب اختيارك",
                "تحديد ونحت الدقن بالموس والفوطة الساخنة",
                "غسيل الشعر + استشوار وتصفيف وواكس",
                "حمام بخار أوزون وتنظيف عميق للبشرة",
                "ماسك فحم أسود + ماسك ذهب للنضارة",
                "مساج مريح للرأس والوجه",
            ),
            isFeatured = true,
        ),
        SalonPackage(
            id = "cut_blowdry",
            title = "باكيدج القصة والاستشوار العصري",
            subtitle = "إطلالة متجددة وتصفيف أنيق يدوم",
            durationLabel = "35 دقيقة",
            badge = "سريع وأنيق",
            services = listOf(
                "قصة شعر عصرية أو كلاسيك مع تدريج متقن",
                "غسيل الشعر بشامبو منعش وبلسم",
                "استشوار وتصفيف حراري احترافي",
                "سيروم لمعان ومثبت تصفيف",
            ),
        ),
        SalonPackage(
            id = "hair_beard_combo",
            title = "باكيدج الشعر والدقن المتكامل",
            subtitle = "تناغم مثالي بين تدريج الشعر وتحديد اللحية",
            durationLabel = "45 دقيقة",
            badge = "كلاسيك الرجال",
            services = listOf(
                "قصة وتدريج شعر (Skin Fade / Taper / Classic)",
                "حلاقة أو تدريج وتحديد الدقن بدقة",
                "كمادات فوطة دافئة لتهدئة البشرة",
                "مرطب وبلسم وكولونيا فاخرة بعد الحلاقة",
            ),
        ),
        SalonPackage(
            id = "beard_masks_care",
            title = "باكيدج الدقن والماسكات وتنظيف البشرة",
            subtitle = "نضارة فورية للبشرة مع تنسيق اللحية",
            durationLabel = "40 دقيقة",
            badge = "عناية بالبشرة",
            services = listOf(
                "تحديد وتنسيق الدقن والشارب",
                "جلسة بخار أوزون وإزالة الرؤوس السوداء",
                "سنفرة تقشير لطيفة لتجديد الخلايا",
                "ماسك طمي مغربي + ماسك ترطيب وتهدئة",
            ),
        ),
        SalonPackage(
            id = "hair_protein_care",
            title = "باكيدج حمام الكريم وعلاج الشعر",
            subtitle = "تغذية عميقة وانسيابية للشعر المجهد",
            durationLabel = "55 دقيقة",
            badge = "عناية مكثفة",
            services = listOf(
                "قص وتنسيق أطراف الشعر والتدريج الجانبي",
                "جلسة حمام كريم ترميمي بالبخار",
                "أمبول تغذية ولمعان لفروة الرأس",
                "استشوار وتصفيف نهائي احترافي",
            ),
        ),
        SalonPackage(
            id = "groom_package",
            title = "باكيدج العريس والمناسبات الخاصة",
            subtitle = "تجهيز متكامل لأهم ليلة بأعلى درجات الفخامة",
            durationLabel = "90 دقيقة",
            badge = "تجهيز خاص",
            services = listOf(
                "قصة شعر وتدريج خاص يناسب ملامح الوجه",
                "نحت وتحديد الدقن بدقة احترافية",
                "تنظيف بشرة ملكي 5 مراحل بالبخار والماسكات",
                "ماسك تفتيح ونضارة فورية + مساج استرخائي",
                "تصفيف شعر احترافي وثبات طوال اليوم",
            ),
        ),
    )

    val haircutCatalog: List<HaircutStyle> = listOf(
        HaircutStyle(
            id = "skin_fade_quiff",
            englishName = "Mid Skin Fade & Quiff",
            arabicName = "سكين فيد مع كويف عصري",
            category = "تدريج فيد",
            faceShape = "الوجه البيضاوي والمربع والقلب",
            stylingTip = "يُصفف بالاستشوار ومات كلاي (Matte Clay) لثبات وحجم طبيعي طوال اليوم.",
            description = "أشهر القصات العالمية التي تجمع بين تدريج ناعم يبدأ من الصفر على الجانبين مع كثافة مرتفعة ومموجة بالأعلى وتحديد حاد ومتناسق مع اللحية.",
            imageRes = R.drawable.img_cut_skin_fade_1794814837967,
        ),
        HaircutStyle(
            id = "textured_french_crop",
            englishName = "Textured French Crop",
            arabicName = "فرنش كروب تدريج عصري",
            category = "عصري وشبابي",
            faceShape = "جميع أشكال الوجه وخاصة الوجه الطويل",
            stylingTip = "يحتاج إلى بودرة كثافة (Texture Powder) خفيفة لإبراز خصلات الشعر الأمامية بسهولة.",
            description = "ستايل عالمي عملي وأنيق يتميز بخصلات علوية ذات ملمس بارز وغرة أمامية محددة بدقة مع تدريج منخفض نظيف حول الأذن والرقبة.",
            imageRes = R.drawable.img_cut_textured_crop_1794814839747,
        ),
        HaircutStyle(
            id = "executive_pompadour",
            englishName = "Executive Pompadour",
            arabicName = "بومبادور كلاسيك واستشوار",
            category = "كلاسيك واستشوار",
            faceShape = "الوجه الدائري والمربع والبيضاوي",
            stylingTip = "يعتمد على الاستشوار الاحترافي مع بوميد (Pomade) بلمعة متوسطة لإطلالة رسمية فخمة.",
            description = "قصة الفخامة الكلاسيكية بلمسة عصرية، تمنح الشعر انسيابية وكثافة مسحوبة للخلف مع جوانب متدرجة بعناية ولحية مشذبة باحتراف.",
            imageRes = R.drawable.img_cut_pompadour_1794814840845,
        ),
        HaircutStyle(
            id = "curly_drop_fade",
            englishName = "Curly Drop Taper Fade",
            arabicName = "كيرلي دروب فيد عالمي",
            category = "كيرلي وتدريج",
            faceShape = "الوجه البيضاوي والمثلث والمستدير",
            stylingTip = "يُنصح بكريم تحديد الكيرلي (Curl Cream) للحفاظ على رطوبة ولمعان التموجات دون هيشان.",
            description = "التصميم المثالي لأصحاب الشعر الكيرلي والمموج، يحافظ على حيوية التموجات بالأعلى مع تدريج منحنٍ خلف الأذن وتحديد حاد لمنبت الشعر.",
            imageRes = R.drawable.img_cut_curly_taper_1794814840745,
        ),
    )
}
