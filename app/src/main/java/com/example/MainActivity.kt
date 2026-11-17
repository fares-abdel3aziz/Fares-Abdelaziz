package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthManager
import com.example.auth.PhoneOtpSessionManager
import com.example.auth.VerifiedPhoneSession
import com.example.auth.authStateFlow
import com.example.data.model.SalonCatalog
import com.example.data.repository.BookingRepository
import com.example.data.repository.PhoneLocalBookingRepository
import com.example.notifications.AppointmentReminderReceiver
import com.example.notifications.ReminderScheduler
import com.example.ui.SalonViewModel
import com.example.ui.UiState
import com.example.ui.screens.AdhkarScreen
import com.example.ui.screens.AppointmentsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BookingScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OwnerDashboardScreen
import com.example.ui.screens.isUpcomingReservation
import com.example.ui.theme.AboAlAtaTheme
import com.example.util.MessageShareHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class SalonDestination(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    CATALOG("catalog", R.string.nav_catalog, Icons.Filled.ContentCut, Icons.Outlined.ContentCut),
    BOOK("book", R.string.nav_book, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    ADHKAR("adhkar", R.string.nav_adhkar, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    APPOINTMENTS("appointments", R.string.nav_appointments, Icons.AutoMirrored.Filled.EventNote, Icons.AutoMirrored.Outlined.EventNote),
    OWNER_DASHBOARD("owner_dashboard", R.string.nav_owner_dashboard, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings),
}

sealed interface ActiveSalonSession {
    val sessionKey: String
    val displayIdentity: String?
    val defaultDisplayName: String
    val defaultPhone: String
    val isSalonOwner: Boolean

    data class VerifiedPhone(val phoneSession: VerifiedPhoneSession) : ActiveSalonSession {
        override val sessionKey: String = phoneSession.sessionId
        override val displayIdentity: String = "رقم الهاتف الموثّق: \u200E${phoneSession.localPhone}"
        override val defaultDisplayName: String = ""
        override val defaultPhone: String = phoneSession.localPhone
        override val isSalonOwner: Boolean = false
    }

    data class CloudGoogle(
        val user: FirebaseUser,
        val fallbackPhone: String = "",
    ) : ActiveSalonSession {
        override val sessionKey: String = user.uid
        private val formattedFirebasePhone: String? =
            user.phoneNumber?.takeIf { it.isNotBlank() }?.let { PhoneOtpSessionManager.toLocalEgyptianDisplay(it) }
        override val displayIdentity: String? =
            formattedFirebasePhone?.let { "رقم الهاتف الموثّق: \u200E$it" } ?: user.email
        override val defaultDisplayName: String = user.displayName ?: ""
        override val defaultPhone: String =
            formattedFirebasePhone ?: fallbackPhone
        override val isSalonOwner: Boolean = SalonCatalog.isVerifiedSalonOwner(
            email = user.email,
            isEmailVerified = user.isEmailVerified,
            phoneE164 = user.phoneNumber,
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppointmentReminderReceiver.ensureChannel(this)
        PhoneOtpSessionManager.ensureOtpNotificationChannel(this)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AboAlAtaTheme(darkTheme = true) {
                    AboAlAtaAppRoot()
                }
            }
        }
    }
}

@Composable
fun AboAlAtaAppRoot() {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val firebaseUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val verifiedPhoneSession by remember(context) {
        PhoneOtpSessionManager.observeSession(context)
    }.collectAsStateWithLifecycle()

    val activeSession: ActiveSalonSession? = when {
        firebaseUser != null -> ActiveSalonSession.CloudGoogle(
            user = firebaseUser!!,
            fallbackPhone = verifiedPhoneSession?.localPhone.orEmpty(),
        )
        verifiedPhoneSession != null -> ActiveSalonSession.VerifiedPhone(verifiedPhoneSession!!)
        else -> null
    }

    if (activeSession == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(activeSession.sessionKey, activeSession.isSalonOwner) {
            AuthenticatedSalonShell(session = activeSession)
        }
    }
}

@Composable
private fun AuthenticatedSalonShell(session: ActiveSalonSession) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SalonViewModel = viewModel(
        key = "${session.sessionKey}_${session.isSalonOwner}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dataSource = when (session) {
                    is ActiveSalonSession.CloudGoogle -> {
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        BookingRepository(db)
                    }
                    is ActiveSalonSession.VerifiedPhone -> {
                        PhoneLocalBookingRepository(
                            context = app,
                            phoneSessionId = session.phoneSession.sessionId,
                        )
                    }
                }
                SalonViewModel(
                    repository = dataSource,
                    currentUserId = session.sessionKey,
                    defaultDisplayName = session.defaultDisplayName,
                    defaultPhone = session.defaultPhone,
                    isSalonOwner = session.isSalonOwner,
                )
            }
        }
    )

    val draft by viewModel.bookingDraft.collectAsStateWithLifecycle()
    val appointmentsState by viewModel.appointmentsState.collectAsStateWithLifecycle()
    val ownerBookingsState by viewModel.ownerBookingsState.collectAsStateWithLifecycle()
    val preference by viewModel.preferenceState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val barbers = remember(preference) { SalonCatalog.getBarbers(preference) }
    val salonPhone = preference?.salonWhatsappPhone?.ifBlank { SalonCatalog.DEFAULT_SALON_PHONE }
        ?: SalonCatalog.DEFAULT_SALON_PHONE

    val upcomingAppointment = remember(appointmentsState) {
        val list = (appointmentsState as? UiState.Success)?.data.orEmpty()
        list.filter { isUpcomingReservation(it) }
            .minByOrNull { it.appointmentEpochMillis }
    }

    val visibleDestinations = remember(session.isSalonOwner) {
        SalonDestination.entries.filter { dest ->
            dest != SalonDestination.OWNER_DASHBOARD || session.isSalonOwner
        }
    }

    var currentDestination by rememberSaveable(session.isSalonOwner) {
        mutableStateOf(
            if (session.isSalonOwner) SalonDestination.OWNER_DASHBOARD else SalonDestination.HOME
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        visibleDestinations.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.routeId}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isExpanded) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        visibleDestinations.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_tab_${dest.routeId}"),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentDestination) {
                        SalonDestination.HOME -> {
                            HomeScreen(
                                barbers = barbers,
                                salonPhone = salonPhone,
                                upcomingAppointment = upcomingAppointment,
                                onSelectBarberAndBook = { barberId ->
                                    viewModel.selectBarber(barberId)
                                    currentDestination = SalonDestination.BOOK
                                },
                                onSelectPackageAndBook = { packageId ->
                                    viewModel.selectPackage(packageId)
                                    currentDestination = SalonDestination.BOOK
                                },
                                onSelectCutAndBook = { style ->
                                    viewModel.selectHaircutStyle(style)
                                    currentDestination = SalonDestination.BOOK
                                },
                                onOpenCatalog = {
                                    currentDestination = SalonDestination.CATALOG
                                },
                                onOpenAppointments = {
                                    currentDestination = SalonDestination.APPOINTMENTS
                                },
                                onOpenAdhkar = {
                                    currentDestination = SalonDestination.ADHKAR
                                },
                            )
                        }

                        SalonDestination.ADHKAR -> {
                            AdhkarScreen(
                                onNavigateBackHome = {
                                    currentDestination = SalonDestination.HOME
                                },
                            )
                        }

                        SalonDestination.CATALOG -> {
                            CatalogScreen(
                                onBookHaircut = { style ->
                                    viewModel.selectHaircutStyle(style)
                                    currentDestination = SalonDestination.BOOK
                                },
                                onNavigateBackHome = {
                                    currentDestination = SalonDestination.HOME
                                },
                            )
                        }

                        SalonDestination.BOOK -> {
                            BookingScreen(
                                draft = draft,
                                barbers = barbers,
                                dates = viewModel.availableDates,
                                timeSlots = viewModel.availableTimeSlots,
                                salonPhone = salonPhone,
                                onNameChange = viewModel::updateCustomerName,
                                onPhoneChange = viewModel::updateCustomerPhone,
                                onBarberSelect = viewModel::selectBarber,
                                onPackageSelect = viewModel::selectPackage,
                                onHaircutSelect = { fullName ->
                                    val matched = SalonCatalog.haircutCatalog.find { it.fullDisplayName == fullName }
                                    viewModel.selectHaircutStyle(matched)
                                },
                                onDateSelect = viewModel::selectDateIndex,
                                onTimeSelect = viewModel::selectTimeIndex,
                                onReminderToggle = viewModel::updateReminderEnabled,
                                onNotesChange = viewModel::updateNotes,
                                onSubmitBooking = {
                                    viewModel.submitBooking(barbers) { createdAppointment ->
                                        ReminderScheduler.scheduleAppointmentNotifications(
                                            context = context,
                                            appointment = createdAppointment,
                                            postImmediateConfirmation = true,
                                        )
                                    }
                                },
                                onSubmitAndConfirmWhatsApp = {
                                    viewModel.submitBooking(barbers) { createdAppointment ->
                                        ReminderScheduler.scheduleAppointmentNotifications(
                                            context = context,
                                            appointment = createdAppointment,
                                            postImmediateConfirmation = true,
                                        )
                                        MessageShareHelper.launchWhatsAppSalonConfirmation(
                                            context = context,
                                            appointment = createdAppointment,
                                            salonPhone = salonPhone,
                                        )
                                    }
                                },
                                onDismissConfirmation = viewModel::dismissConfirmedSheet,
                                onViewMyAppointments = {
                                    currentDestination = SalonDestination.APPOINTMENTS
                                },
                                onNavigateBackHome = {
                                    currentDestination = SalonDestination.HOME
                                },
                            )
                        }

                        SalonDestination.APPOINTMENTS -> {
                            AppointmentsScreen(
                                appointmentsState = appointmentsState,
                                preference = preference,
                                userEmail = session.displayIdentity,
                                onMarkCompleted = { appt ->
                                    ReminderScheduler.cancelReminder(context, appt.id)
                                    viewModel.markAppointmentCompleted(appt.id)
                                },
                                onCancelAppointment = { appt ->
                                    ReminderScheduler.cancelReminder(context, appt.id)
                                    viewModel.cancelAppointment(appt.id)
                                },
                                onDeleteAppointment = { appt ->
                                    ReminderScheduler.cancelReminder(context, appt.id)
                                    viewModel.deleteAppointment(appt.id)
                                },
                                onSubmitFeedback = { appt, rating, comment ->
                                    viewModel.submitAppointmentFeedback(appt.id, rating, comment)
                                },
                                onSaveSettings = viewModel::saveSalonSettings,
                                onBookNewClick = {
                                    currentDestination = SalonDestination.BOOK
                                },
                                onSignOut = {
                                    AuthManager.signOut(context, credentialManager, coroutineScope)
                                },
                                onNavigateBackHome = {
                                    currentDestination = SalonDestination.HOME
                                },
                            )
                        }

                        SalonDestination.OWNER_DASHBOARD -> {
                            if (session.isSalonOwner) {
                                OwnerDashboardScreen(
                                    ownerBookingsState = ownerBookingsState,
                                    barbers = barbers,
                                    ownerIdentity = session.displayIdentity,
                                    onMarkCompleted = { appt ->
                                        viewModel.updateOwnerBookingStatus(appt, "completed")
                                    },
                                    onCancelBooking = { appt ->
                                        viewModel.updateOwnerBookingStatus(appt, "cancelled")
                                    },
                                    onNavigateBackHome = {
                                        currentDestination = SalonDestination.HOME
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
