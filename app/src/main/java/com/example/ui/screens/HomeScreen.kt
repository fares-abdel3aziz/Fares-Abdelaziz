package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Appointment
import com.example.data.model.BarberProfile
import com.example.data.model.HaircutStyle
import com.example.data.model.SalonCatalog
import com.example.data.model.SalonPackage
import com.example.util.MessageShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    barbers: List<BarberProfile>,
    salonPhone: String,
    upcomingAppointment: Appointment?,
    onSelectBarberAndBook: (String) -> Unit,
    onSelectPackageAndBook: (String) -> Unit,
    onSelectCutAndBook: (HaircutStyle) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenAdhkar: () -> Unit = {},
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    topAuthSlot: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(LandingMenuFilter.ALL) }
    var activePackageDetail by remember { mutableStateOf<SalonPackage?>(null) }
    var activeCutDetail by remember { mutableStateOf<HaircutStyle?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_lazy_column"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("landing_menu_grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Optional top sign-in banner when unauthenticated
            if (topAuthSlot != null) {
                item(key = "auth_banner", span = { GridItemSpan(maxLineSpan) }) {
                    topAuthSlot()
                }
            }

            // 1. Modern Salon Header with Salon Name & Contact Numbers Strip
            item(key = "salon_header", span = { GridItemSpan(maxLineSpan) }) {
                SalonLandingHeader(
                    salonPhone = salonPhone,
                    onBookNowClick = { onSelectBarberAndBook("mody") },
                    onCallShopDirect = {
                        MessageShareHelper.dialSalon(context, SalonCatalog.SHOP_DIRECT_PHONE)
                    },
                    onCallSalonBooking = {
                        MessageShareHelper.dialSalon(context, salonPhone)
                    },
                    onWhatsAppSalon = {
                        MessageShareHelper.sendWhatsAppMessage(
                            context = context,
                            targetPhone = salonPhone,
                            message = "السلام عليكم صالون أبو العطا، أريد الاستفسار عن حجز موعد حلاقة.",
                        )
                    },
                )
            }

            // 2. Active Upcoming Appointment Banner (when confirmed booking exists)
            if (upcomingAppointment != null) {
                item(key = "upcoming_banner", span = { GridItemSpan(maxLineSpan) }) {
                    UpcomingAppointmentBanner(
                        appointment = upcomingAppointment,
                        onOpenAppointments = onOpenAppointments,
                    )
                }
            }

            // 2b. Adhkar & Salawat Upon the Prophet Quick Banner
            item(key = "adhkar_banner", span = { GridItemSpan(maxLineSpan) }) {
                LandingAdhkarCard(onOpenAdhkar = onOpenAdhkar)
            }

            // 3. Quick Grid Menu Bento + Section Filter Chips
            item(key = "quick_menu_bento", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.landing_menu_title),
                        subtitle = stringResource(R.string.landing_menu_subtitle),
                    )
                    LandingMenuBentoRow(
                        selectedFilter = selectedFilter,
                        onSelectFilter = { selectedFilter = it },
                        onQuickBookClick = { onSelectBarberAndBook("mody") },
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LandingMenuFilter.entries, key = { it.name }) { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(stringResource(filter.labelRes)) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("landing_filter_${filter.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }

            // 4. Service Packages Grid (No prices — full service breakdown)
            if (selectedFilter == LandingMenuFilter.ALL || selectedFilter == LandingMenuFilter.PACKAGES) {
                item(key = "packages_header", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        title = stringResource(R.string.section_packages),
                        subtitle = stringResource(R.string.packages_subtitle),
                    )
                }

                items(
                    items = SalonCatalog.packages,
                    key = { "pkg_${it.id}" },
                ) { pkg ->
                    PackageGridCard(
                        salonPackage = pkg,
                        onOpenDetails = { activePackageDetail = pkg },
                        onBookPackage = { onSelectPackageAndBook(pkg.id) },
                    )
                }
            }

            // 5. Global Haircut Catalog Grid
            if (selectedFilter == LandingMenuFilter.ALL || selectedFilter == LandingMenuFilter.CATALOG) {
                item(key = "catalog_header", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.catalog_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = stringResource(R.string.catalog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onOpenCatalog,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("home_open_catalog_button"),
                        ) {
                            Text(stringResource(R.string.landing_view_full_catalog))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                items(
                    items = SalonCatalog.haircutCatalog,
                    key = { "cut_${it.id}" },
                ) { cut ->
                    HaircutCatalogGridCard(
                        style = cut,
                        onOpenDetails = { activeCutDetail = cut },
                        onBookStyle = { onSelectCutAndBook(cut) },
                    )
                }
            }

            // 6. Barbers Roster Grid (Mody + Barber 2 + Barber 3)
            if (selectedFilter == LandingMenuFilter.ALL || selectedFilter == LandingMenuFilter.BARBERS) {
                item(key = "barbers_header", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        title = stringResource(R.string.section_barbers),
                        subtitle = "اختر الصنايعي المفضل لديك واحجز ميعادك فوراً",
                    )
                }

                items(
                    items = barbers,
                    key = { "barber_${it.id}" },
                ) { barber ->
                    BarberGridCard(
                        barber = barber,
                        onBookWithBarber = { onSelectBarberAndBook(barber.id) },
                    )
                }
            }
        }
    }

    // Package Details Modal Sheet
    val selectedPackage = activePackageDetail
    if (selectedPackage != null) {
        PackageDetailBottomSheet(
            salonPackage = selectedPackage,
            onDismiss = { activePackageDetail = null },
            onBookPackage = {
                val id = selectedPackage.id
                activePackageDetail = null
                onSelectPackageAndBook(id)
            },
        )
    }

    // Haircut Style Preview Modal Sheet
    val selectedCut = activeCutDetail
    if (selectedCut != null) {
        ModalBottomSheet(
            onDismissRequest = { activeCutDetail = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(id = selectedCut.imageRes),
                    contentDescription = selectedCut.arabicName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = selectedCut.arabicName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = selectedCut.englishName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = selectedCut.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مناسبة لـ: ${selectedCut.faceShape}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedCut.stylingTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val chosen = selectedCut
                        activeCutDetail = null
                        onSelectCutAndBook(chosen)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("home_sheet_book_cut_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(imageVector = Icons.Filled.ContentCut, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_book_this_cut))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UpcomingAppointmentBanner(
    appointment: Appointment,
    onOpenAppointments: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpenAppointments)
            .testTag("upcoming_appointment_banner"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "موعدك القادم • ${appointment.appointmentDate} (${appointment.appointmentTime})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "مع ${appointment.barberName} • ${appointment.packageTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "عرض تفاصيل الموعد",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LandingAdhkarCard(
    onOpenAdhkar: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpenAdhkar)
            .testTag("home_open_adhkar_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = stringResource(R.string.landing_adhkar_card_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.landing_adhkar_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.landing_adhkar_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onOpenAdhkar,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("home_open_adhkar_button"),
            ) {
                Text(stringResource(R.string.landing_open_adhkar_btn))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
