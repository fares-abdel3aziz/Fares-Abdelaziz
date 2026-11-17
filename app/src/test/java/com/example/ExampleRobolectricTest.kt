package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.auth.AuthManager
import com.example.auth.PhoneOtpSessionManager
import com.example.data.model.Appointment
import com.example.data.model.SalonCatalog
import com.example.data.repository.PhoneLocalBookingRepository
import com.example.notifications.AppointmentReminderWorker
import com.example.notifications.ReminderScheduler
import com.example.ui.BookingDraftState
import com.example.ui.DateOption
import com.example.ui.TimeSlotOption
import com.example.ui.UiState
import com.example.ui.screens.AdhkarScreen
import com.example.ui.screens.AppointmentsScreen
import com.example.ui.screens.BookingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OwnerDashboardScreen
import com.example.ui.screens.PhoneOtpSignInCard
import com.example.ui.theme.AboAlAtaTheme
import com.example.util.MessageShareHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.app.NotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app name and salon catalog defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("صالون أبو العطا", appName)

        val barbers = SalonCatalog.getBarbers(null)
        assertEquals(3, barbers.size)
        assertEquals("مودي", barbers[0].defaultName)
        assertEquals("صنايعي 2", barbers[1].defaultName)
        assertEquals("صنايعي 3", barbers[2].defaultName)
    }

    @Test
    fun `phone otp session dispatches local notification without launching Google or browser and persists bookings`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PhoneOtpSessionManager.clearSession(app)
        // Drain any prior started activities from earlier tests
        while (shadowOf(app).nextStartedActivity != null) { /* clear queue */ }

        // Grant POST_NOTIFICATIONS on SDK 36 (mirrors AuthScreen's RequestPermission launcher)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val dispatch = PhoneOtpSessionManager.sendOtpCode(app, "01029898309").getOrThrow()
        assertEquals("+201029898309", dispatch.phoneE164)
        assertEquals("01029898309", dispatch.localPhone)
        assertEquals(6, dispatch.otpCode.length)
        assertTrue(dispatch.notificationPosted)

        // Verify ZERO external Google/Chrome activities were launched when sending OTP
        assertNull("Expected no browser or Google redirect on sendOtpCode", shadowOf(app).nextStartedActivity)

        // Verify notification was posted to NotificationManager on the phone
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val postedNotifs = shadowOf(nm).allNotifications
        assertTrue(postedNotifs.isNotEmpty())

        // Verify 6-digit OTP creates persistent VerifiedPhoneSession
        val session = PhoneOtpSessionManager.verifyOtpCode(app, dispatch.otpCode).getOrThrow()
        assertEquals("01029898309", session.localPhone)
        assertEquals(session, PhoneOtpSessionManager.currentSession(app))

        // Verify PhoneLocalBookingRepository persists appointment + 5-star feedback across instances
        val repo1 = PhoneLocalBookingRepository(app, session.sessionId)
        val created = repo1.createAppointment(
            Appointment(
                customerName = "فارس",
                customerPhone = session.localPhone,
                barberName = "مودي",
                packageId = "vip_royal",
                packageTitle = "باكيدج أبو العطا الملكي",
                packageServices = "عناية كاملة",
                appointmentDate = "اليوم ١٦ نوفمبر",
                appointmentTime = "8:00 مساءً",
                appointmentEpochMillis = 1_800_050_000_000L,
            )
        )
        repo1.updateAppointmentStatus(created.id, "completed", reminderScheduled = false)
        repo1.submitAppointmentFeedback(created.id, 5, "خدمة ممتازة")

        val repo2 = PhoneLocalBookingRepository(app, session.sessionId)
        val reloaded = repo2.observeAppointments(session.sessionId).first().find { it.id == created.id }
        assertNotNull(reloaded)
        assertEquals("completed", reloaded?.status)
        assertEquals(5L, reloaded?.rating)
        assertEquals("خدمة ممتازة", reloaded?.feedbackComment)

        PhoneOtpSessionManager.clearSession(app)
        assertNull(PhoneOtpSessionManager.currentSession(app))
    }

    @Test
    fun `egypt only phone auth validates +20 mobile numbers rejects non-Egyptian numbers and renders OTP UI without Chrome`() {
        // Valid Egyptian mobile numbers (010, 011, 012, 015)
        assertEquals("+201029898309", com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("01029898309"))
        assertEquals("+201020484470", com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("٠١٠٢٠٤٨٤٤٧٠"))
        assertEquals("+201112223334", com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("1112223334"))
        assertEquals("+201223334445", com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("01223334445"))
        assertEquals("+201556667778", com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("01556667778"))
        assertEquals("+201029898309", com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("+20 102 989 8309"))

        // Non-Egyptian numbers & invalid prefixes MUST be rejected (Egypt only)
        assertNull(com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("+966501234567"))
        assertNull(com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("+971521234567"))
        assertNull(com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("0501234567"))
        assertNull(com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("01912345678"))
        assertNull(com.example.auth.FirebasePhoneAuthHelper.formatEgyptianPhoneToE164("12345"))
        assertEquals("654321", AuthManager.normalizeOtpDigits("٦٥٤٣٢١"))

        val fallbackOutcome = com.example.auth.FirebasePhoneAuthHelper.mapFirebasePhoneException(
            com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException()
        )
        assertTrue(fallbackOutcome.shouldFallbackToInAppOtp)

        var phone by mutableStateOf("")
        var otp by mutableStateOf("")
        var isOtpStep by mutableStateOf(false)
        var showSmsSetup by mutableStateOf(true)
        var verifiedClicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    PhoneOtpSignInCard(
                        phoneInput = phone,
                        otpInput = otp,
                        isOtpStep = isOtpStep,
                        sentToPhoneDisplay = "01029898309",
                        resendSecondsRemaining = 30,
                        isLoading = false,
                        errorMessage = null,
                        onPhoneInputChange = { phone = it },
                        onOtpInputChange = { otp = it },
                        onSendOtpClick = {
                            showSmsSetup = false
                            isOtpStep = true
                        },
                        onVerifyOtpClick = { verifiedClicked = true },
                        onResendOtpClick = {},
                        onChangePhoneClick = { isOtpStep = false },
                        onGoogleFallbackClick = {},
                        showSmsProviderSetupCard = showSmsSetup,
                        onStartDemoNotificationOtpClick = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("egypt_only_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phone_auth_sms_setup_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phone_auth_number_input").performTextInput("01029898309")
        composeTestRule.onNodeWithTag("phone_auth_send_otp_button").performClick()
        composeTestRule.waitForIdle()

        // Step 2: 6-digit OTP verification UI starts blank (never auto-filled) and accepts manual 6-digit entry
        assertEquals("", otp)
        composeTestRule.onNodeWithTag("phone_auth_otp_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phone_auth_otp_input").performTextInput("123456")
        composeTestRule.waitForIdle()
        assertEquals("123456", otp)
        composeTestRule.onNodeWithTag("phone_auth_verify_otp_button").performClick()
        assertTrue(verifiedClicked)
    }

    @Test
    fun `workManager schedules local notification 30 minutes before appointment and cancels cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        val fixedNow = 1_800_000_000_000L
        val twoHoursMs = 2L * 60L * 60L * 1000L
        val expectedDelayMs = 90L * 60L * 1000L // 120m - 30m = 90m

        assertEquals(
            expectedDelayMs,
            ReminderScheduler.computeThirtyMinuteReminderDelayMillis(
                appointmentEpochMillis = fixedNow + twoHoursMs,
                nowMillis = fixedNow,
            )
        )

        val appointment = Appointment(
            id = "appt_wm_1",
            customerName = "فارس",
            customerPhone = "01029898309",
            barberName = "مودي",
            packageTitle = "باكيدج أبو العطا الملكي (VIP)",
            appointmentDate = "غداً ١٧ نوفمبر",
            appointmentTime = "7:00 مساءً",
            appointmentEpochMillis = fixedNow + twoHoursMs,
            reminderScheduled = true,
        )

        val scheduled = ReminderScheduler.scheduleAppointmentNotifications(
            context = context,
            appointment = appointment,
            postImmediateConfirmation = false,
            nowMillis = fixedNow,
        )
        assertTrue(scheduled)

        val workManager = WorkManager.getInstance(context)
        val uniqueName = ReminderScheduler.uniqueWorkName("appt_wm_1")
        val enqueuedInfos = workManager.getWorkInfosForUniqueWork(uniqueName).get()
        assertEquals(1, enqueuedInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, enqueuedInfos.first().state)

        // Execute worker directly via TestListenableWorkerBuilder
        val worker = TestListenableWorkerBuilder<AppointmentReminderWorker>(context)
            .setInputData(
                workDataOf(
                    AppointmentReminderWorker.KEY_CUSTOMER_NAME to "فارس",
                    AppointmentReminderWorker.KEY_BARBER_NAME to "مودي",
                    AppointmentReminderWorker.KEY_PACKAGE_TITLE to "باكيدج أبو العطا الملكي",
                    AppointmentReminderWorker.KEY_APPOINTMENT_TIME to "7:00 مساءً",
                )
            )
            .build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        // Cancel reminder and verify WorkManager job transitions to CANCELLED
        ReminderScheduler.cancelReminder(context, "appt_wm_1")
        val cancelledInfos = workManager.getWorkInfosForUniqueWork(uniqueName).get()
        assertEquals(WorkInfo.State.CANCELLED, cancelledInfos.first().state)
    }

    @Test
    fun `launchWhatsAppSalonConfirmation fires prefilled WhatsApp intent to salon phone`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val appointment = Appointment(
            customerName = "محمود",
            customerPhone = "01012345678",
            barberName = "مودي",
            packageTitle = "باكيدج القصة والاستشوار العصري",
            packageServices = "قصة شعر + استشوار",
            appointmentDate = "غداً (الثلاثاء) ١٧ نوفمبر",
            appointmentTime = "5:30 مساءً",
        )

        MessageShareHelper.launchWhatsAppSalonConfirmation(
            context = app,
            appointment = appointment,
            salonPhone = "01029898309",
        )

        val startedIntent = shadowOf(app).nextStartedActivity
        assertNotNull("Expected WhatsApp ACTION_VIEW intent to be launched", startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)

        val uri = startedIntent.data
        assertNotNull(uri)
        assertTrue(uri.toString().contains("201029898309"))

        val decodedText = Uri.decode(uri!!.getQueryParameter("text"))
        assertTrue(decodedText.contains("صالون أبو العطا"))
        assertTrue(decodedText.contains("محمود"))
        assertTrue(decodedText.contains("مودي"))
        assertTrue(decodedText.contains("باكيدج القصة والاستشوار العصري"))
        assertTrue(decodedText.contains("5:30 مساءً"))
        assertTrue(decodedText.contains("0102048447"))
    }

    @Test
    fun `landing screen renders salon header contact numbers and grid menu`() {
        var selectedPackageId: String? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    HomeScreen(
                        barbers = SalonCatalog.getBarbers(null),
                        salonPhone = SalonCatalog.DEFAULT_SALON_PHONE,
                        upcomingAppointment = null,
                        onSelectBarberAndBook = {},
                        onSelectPackageAndBook = { selectedPackageId = it },
                        onSelectCutAndBook = {},
                        onOpenCatalog = {},
                        onOpenAppointments = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("صالون أبو العطا").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_call_shop_direct_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_call_salon_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_whatsapp_salon_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("landing_menu_grid")
            .performScrollToNode(hasTestTag("select_package_vip_royal"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("package_card_vip_royal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_package_vip_royal").performClick()
        assertEquals("vip_royal", selectedPackageId)

        composeTestRule.onNodeWithTag("landing_menu_grid")
            .performScrollToNode(hasTestTag("preview_cut_skin_fade_quiff"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_cut_skin_fade_quiff").assertIsDisplayed()
    }

    @Test
    fun `adhkar screen renders salawat counter and increments on click`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    AdhkarScreen(onNavigateBackHome = {})
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("قائمة الأذكار والصلاة على النبي ﷺ").assertIsDisplayed()
        composeTestRule.onNodeWithTag("salawat_counter_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("salawat_increment_cta").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("مجموع التسبيح والصلاة: 1").assertIsDisplayed()
    }

    @Test
    fun `reservation form selects barber package date time and triggers save`() {
        var draft by mutableStateOf(BookingDraftState())
        var submitted = false
        var submittedWithWhatsApp = false

        val dates = listOf(
            DateOption("2026-11-16", "اليوم (الإثنين)", "١٦", "نوفمبر", 2026, 10, 16),
            DateOption("2026-11-17", "غداً (الثلاثاء)", "١٧", "نوفمبر", 2026, 10, 17),
        )
        val slots = listOf(
            TimeSlotOption("15_00", "3:00 مساءً", 15, 0),
            TimeSlotOption("17_30", "5:30 مساءً", 17, 30),
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    BookingScreen(
                        draft = draft,
                        barbers = SalonCatalog.getBarbers(null),
                        dates = dates,
                        timeSlots = slots,
                        salonPhone = SalonCatalog.DEFAULT_SALON_PHONE,
                        onNameChange = { draft = draft.copy(customerName = it) },
                        onPhoneChange = { draft = draft.copy(customerPhone = it) },
                        onBarberSelect = { draft = draft.copy(selectedBarberId = it) },
                        onPackageSelect = { draft = draft.copy(selectedPackageId = it) },
                        onHaircutSelect = { draft = draft.copy(selectedHaircutName = it) },
                        onDateSelect = { draft = draft.copy(selectedDateIndex = it) },
                        onTimeSelect = { draft = draft.copy(selectedTimeIndex = it) },
                        onReminderToggle = { draft = draft.copy(reminderEnabled = it) },
                        onNotesChange = { draft = draft.copy(notes = it) },
                        onSubmitBooking = { submitted = true },
                        onSubmitAndConfirmWhatsApp = { submittedWithWhatsApp = true },
                        onDismissConfirmation = {},
                        onViewMyAppointments = {},
                        onNavigateBackHome = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("booking_lazy_column")
            .performScrollToNode(hasTestTag("booking_name_input"))
        composeTestRule.onNodeWithTag("booking_name_input").performTextInput("فارس")
        composeTestRule.onNodeWithTag("booking_phone_input").performTextInput("01099887766")

        composeTestRule.onNodeWithTag("booking_lazy_column")
            .performScrollToNode(hasTestTag("booking_barber_option_barber_2"))
        composeTestRule.onNodeWithTag("booking_barber_option_barber_2").performClick()

        composeTestRule.onNodeWithTag("booking_lazy_column")
            .performScrollToNode(hasTestTag("booking_package_option_cut_blowdry"))
        composeTestRule.onNodeWithTag("booking_package_option_cut_blowdry").performClick()

        composeTestRule.onNodeWithTag("booking_lazy_column")
            .performScrollToNode(hasTestTag("booking_time_17_30"))
        composeTestRule.onNodeWithTag("booking_date_1").performClick()
        composeTestRule.onNodeWithTag("booking_time_17_30").performClick()

        composeTestRule.onNodeWithTag("booking_lazy_column")
            .performScrollToNode(hasTestTag("confirm_and_whatsapp_salon_button"))
        composeTestRule.onNodeWithTag("confirm_booking_button").performClick()
        composeTestRule.onNodeWithTag("confirm_and_whatsapp_salon_button").performClick()

        assertEquals("barber_2", draft.selectedBarberId)
        assertEquals("cut_blowdry", draft.selectedPackageId)
        assertEquals(1, draft.selectedDateIndex)
        assertEquals(1, draft.selectedTimeIndex)
        assertTrue(submitted)
        assertTrue(submittedWithWhatsApp)
    }

    @Test
    fun `my bookings screen cancels upcoming appointment and submits feedback on completed appointment`() {
        val fixedNow = 1_800_000_000_000L
        val upcomingBooking = Appointment(
            id = "upcoming_1",
            customerName = "فارس",
            customerPhone = "01099887766",
            barberName = "مودي",
            packageTitle = "باكيدج أبو العطا الملكي (VIP)",
            appointmentDate = "غداً (الثلاثاء) ١٧ نوفمبر",
            appointmentTime = "6:00 مساءً",
            appointmentEpochMillis = fixedNow + 86_400_000L,
            status = "confirmed",
        )
        val pastCompletedBooking = Appointment(
            id = "past_1",
            customerName = "فارس",
            customerPhone = "01099887766",
            barberName = "صنايعي 2",
            packageTitle = "باكيدج القصة والاستشوار العصري",
            appointmentDate = "الأحد ٩ نوفمبر",
            appointmentTime = "4:00 مساءً",
            appointmentEpochMillis = fixedNow - 604_800_000L,
            status = "completed",
        )

        var cancelledId: String? = null
        var submittedFeedbackTriple: Triple<String, Int, String>? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    AppointmentsScreen(
                        appointmentsState = UiState.Success(
                            listOf(upcomingBooking, pastCompletedBooking)
                        ),
                        preference = null,
                        userEmail = "+201099887766",
                        onMarkCompleted = {},
                        onCancelAppointment = { cancelledId = it.id },
                        onSubmitFeedback = { appt, rating, comment ->
                            submittedFeedbackTriple = Triple(appt.id, rating, comment)
                        },
                        onSaveSettings = { _, _, _, _, _ -> },
                        onBookNewClick = {},
                        onSignOut = {},
                        onNavigateBackHome = {},
                        referenceNowMillis = fixedNow,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("حجوزاتي").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bookings_filter_upcoming").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bookings_filter_past").assertIsDisplayed()

        // Upcoming card & status badge visible -> Cancel via dialog
        composeTestRule.onNodeWithTag("appointments_lazy_column")
            .performScrollToNode(hasTestTag("appt_cancel_upcoming_1"))
        composeTestRule.onNodeWithTag("appt_status_badge_upcoming_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("appt_cancel_upcoming_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cancel_appointment_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_cancel_dialog_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("upcoming_1", cancelledId)

        // Switch to Past tab and submit 4-star feedback on completed booking
        composeTestRule.onNodeWithTag("appointments_lazy_column")
            .performScrollToNode(hasTestTag("bookings_filter_past"))
        composeTestRule.onNodeWithTag("bookings_filter_past").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("appointments_lazy_column")
            .performScrollToNode(hasTestTag("appointment_card_past_1"))
        composeTestRule.onNodeWithTag("feedback_star_past_1_4")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("feedback_comment_past_1")
            .performScrollTo()
            .performTextInput("حلاقة ممتازة وخدمة راقية")
        composeTestRule.onNodeWithTag("feedback_submit_past_1")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(Triple("past_1", 4, "حلاقة ممتازة وخدمة راقية"), submittedFeedbackTriple)
    }

    @Test
    fun `salon owner verification and owner dashboard screen filters todays bookings and marks completed`() {
        assertTrue(
            SalonCatalog.isVerifiedSalonOwner(
                email = "fareselbialy1941@gmail.com",
                isEmailVerified = true,
                phoneE164 = null,
            )
        )
        assertFalse(
            SalonCatalog.isVerifiedSalonOwner(
                email = "fareselbialy1941@gmail.com",
                isEmailVerified = false,
                phoneE164 = null,
            )
        )
        assertFalse(
            SalonCatalog.isVerifiedSalonOwner(
                email = "customer@gmail.com",
                isEmailVerified = true,
                phoneE164 = "+201112223334",
            )
        )
        assertTrue(
            SalonCatalog.isVerifiedSalonOwner(
                email = null,
                isEmailVerified = false,
                phoneE164 = "+201029898309",
            )
        )

        val noonCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 17, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val fixedNow = noonCal.timeInMillis

        val todayBooking = Appointment(
            id = "today_1",
            userId = "cust_uid_1",
            customerName = "أحمد سامي",
            customerPhone = "01012345678",
            barberId = "mody",
            barberName = "مودي",
            packageTitle = "باكيدج أبو العطا الملكي (VIP)",
            appointmentDate = "اليوم ١٧ نوفمبر",
            appointmentTime = "5:00 مساءً",
            appointmentEpochMillis = fixedNow + 2L * 3_600_000L,
            status = "confirmed",
        )
        val tomorrowBooking = Appointment(
            id = "tomorrow_1",
            userId = "cust_uid_2",
            customerName = "كريم طارق",
            customerPhone = "01122334455",
            barberId = "barber_2",
            barberName = "صنايعي 2",
            packageTitle = "باكيدج القصة والاستشوار العصري",
            appointmentDate = "غداً ١٨ نوفمبر",
            appointmentTime = "6:30 مساءً",
            appointmentEpochMillis = fixedNow + 26L * 3_600_000L,
            status = "confirmed",
        )

        var completedApptId: String? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    OwnerDashboardScreen(
                        ownerBookingsState = UiState.Success(listOf(todayBooking, tomorrowBooking)),
                        barbers = SalonCatalog.getBarbers(null),
                        ownerIdentity = "fareselbialy1941@gmail.com",
                        onMarkCompleted = { completedApptId = it.id },
                        onCancelBooking = {},
                        onNavigateBackHome = {},
                        referenceNowMillis = fixedNow,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("owner_dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("owner_filter_today").assertIsDisplayed()

        // Default scope is TODAY: today_1 is displayed and can be marked completed
        composeTestRule.onNodeWithTag("owner_bookings_lazy_column")
            .performScrollToNode(hasTestTag("owner_complete_today_1"))
        composeTestRule.onNodeWithTag("owner_booking_card_today_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("owner_complete_today_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("today_1", completedApptId)

        // Switching to TOMORROW shows tomorrow_1
        composeTestRule.onNodeWithTag("owner_bookings_lazy_column")
            .performScrollToNode(hasTestTag("owner_filter_tomorrow"))
        composeTestRule.onNodeWithTag("owner_filter_tomorrow").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("owner_bookings_lazy_column")
            .performScrollToNode(hasTestTag("owner_booking_card_tomorrow_1"))
        composeTestRule.onNodeWithTag("owner_booking_card_tomorrow_1").assertIsDisplayed()
    }
}
