package com.example.data.model

enum class DhikrCategory(val labelAr: String) {
    ALL("الكل"),
    SALAWAT("الصلاة على النبي ﷺ"),
    TASBIH("التسبيح والاستغفار"),
    MORNING_EVENING("أذكار اليوم والليلة"),
    BARAKAH_DUA("أدعية البركة والحُسن"),
}

data class DhikrItem(
    val id: String,
    val title: String,
    val arabicText: String,
    val virtue: String,
    val category: DhikrCategory,
    val targetCount: Int,
)

object AdhkarCatalog {

    val items: List<DhikrItem> = listOf(
        DhikrItem(
            id = "salawat_ibrahimiyyah",
            title = "الصلاة الإبراهيمية الكاملة",
            arabicText = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ، اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ.",
            virtue = "أكمل صيغ الصلاة على النبي ﷺ؛ من صلى عليه صلاةً صلى الله عليه بها عشراً.",
            category = DhikrCategory.SALAWAT,
            targetCount = 10,
        ),
        DhikrItem(
            id = "salawat_concise",
            title = "الصلاة والتسليم على خير الأنام",
            arabicText = "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى نَبِيِّنَا مُحَمَّدٍ وَعَلَى آلِهِ وَصَحْبِهِ أَجْمَعِينَ.",
            virtue = "تُفرج بها الهموم وتُقضى الحوائج وتُغفر الذنوب بإذن الله.",
            category = DhikrCategory.SALAWAT,
            targetCount = 33,
        ),
        DhikrItem(
            id = "salawat_salam",
            title = "صيغة الصلاة المختصرة",
            arabicText = "صَلَّى اللَّهُ عَلَيْهِ وَعَلَى آلِهِ وَسَلَّمَ.",
            virtue = "ذكر خفيف على اللسان عظيم الأجر في كل وقت وحين.",
            category = DhikrCategory.SALAWAT,
            targetCount = 100,
        ),
        DhikrItem(
            id = "subhan_allah_wabihamdih",
            title = "سبحان الله وبحمده",
            arabicText = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ.",
            virtue = "كلمتان خفيفتان على اللسان، ثقيلتان في الميزان، حبيبتان إلى الرحمن.",
            category = DhikrCategory.TASBIH,
            targetCount = 33,
        ),
        DhikrItem(
            id = "istighfar",
            title = "الاستغفار الجامع",
            arabicText = "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ الَّذِي لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ وَأَتُوبُ إِلَيْهِ.",
            virtue = "سبب لسعة الرزق والبركة وتفريج الكربات.",
            category = DhikrCategory.TASBIH,
            targetCount = 33,
        ),
        DhikrItem(
            id = "baqiyat_salihat",
            title = "الباقيات الصالحات",
            arabicText = "سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ.",
            virtue = "أحب الكلام إلى الله وخير مما طلعت عليه الشمس.",
            category = DhikrCategory.TASBIH,
            targetCount = 33,
        ),
        DhikrItem(
            id = "hawqalah",
            title = "كنز من كنوز الجنة",
            arabicText = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ الْعَلِيِّ الْعَظِيمِ.",
            virtue = "كنز من كنوز الجنة وباب من أبواب العون والتوفيق.",
            category = DhikrCategory.TASBIH,
            targetCount = 10,
        ),
        DhikrItem(
            id = "sayyid_istighfar",
            title = "سيد الاستغفار",
            arabicText = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
            virtue = "من أذكار الصباح والمساء العظيمة لطلب المغفرة والرحمة.",
            category = DhikrCategory.MORNING_EVENING,
            targetCount = 1,
        ),
        DhikrItem(
            id = "bismillah_protection",
            title = "ذكر الحفظ والتحصين",
            arabicText = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.",
            virtue = "من قالها ثلاثاً إذا أصبح وثلاثاً إذا أمسى لم يضره شيء بإذن الله.",
            category = DhikrCategory.MORNING_EVENING,
            targetCount = 3,
        ),
        DhikrItem(
            id = "raditu_billah",
            title = "ذكر الرضا بالله",
            arabicText = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا وَرَسُولًا.",
            virtue = "من قالها ثلاثاً حين يصبح وثلاثاً حين يمسي كان حقاً على الله أن يرضيه.",
            category = DhikrCategory.MORNING_EVENING,
            targetCount = 3,
        ),
        DhikrItem(
            id = "mirror_beautify_dua",
            title = "دعاء النظر في المرآة وحُسن الخُلُق",
            arabicText = "اللَّهُمَّ كَمَا حَسَّنْتَ خَلْقِي فَحَسِّنْ خُلُقِي، وَبَارِكْ لِي فِيمَا رَزَقْتَنِي.",
            virtue = "دعاء مبارك يُقال عند التجمّل والنظر في المرآة شكراً لله على نعمته.",
            category = DhikrCategory.BARAKAH_DUA,
            targetCount = 1,
        ),
        DhikrItem(
            id = "afw_wa_afiyah",
            title = "دعاء العفو والعافية والبركة",
            arabicText = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ، اللَّهُمَّ بَارِكْ لَنَا فِي أَعْمَارِنَا وَأَرْزَاقِنَا وَأَوْقَاتِنَا.",
            virtue = "من أجمع الأدعية لطلب السلامة والبركة في الرزق والعمل.",
            category = DhikrCategory.BARAKAH_DUA,
            targetCount = 3,
        ),
    )
}
