package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.Bookmark
import com.example.NotificationLog
import com.example.R
import com.example.ui.theme.StokVibeBlue
import com.example.ui.theme.StokVibeBorder
import com.example.ui.theme.StokVibeCyan
import com.example.ui.theme.StokVibeEmerald
import com.example.ui.theme.StokVibeIndigo
import com.example.ui.theme.StokVibeNavy
import com.example.ui.theme.StokVibeSlate
import com.example.ui.theme.StokVibeSoftBlue
import com.example.ui.theme.StokVibeSurface
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

private data class OnboardingCardData(
    val stepLabel: String,
    val badge: String,
    val title: String,
    val description: String,
    val howToUseTitle: String,
    val howToUseSteps: List<String>,
    @DrawableRes val imageRes: Int,
    val accentColor: Color,
    val tintColor: Color
)

@Composable
fun StokVibeLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val cyanColor = Color(0xFF00E5FF)
            val blueColor = StokVibeBlue
            val darkColor = Color(0xFF0A192F)

            drawRoundRect(
                color = cyanColor,
                topLeft = Offset(0f, h * 0.35f),
                size = Size(w * 0.45f, h * 0.45f),
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = cyanColor,
                topLeft = Offset(w * 0.22f, h * 0.55f),
                size = Size(w * 0.23f, h * 0.35f),
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = blueColor,
                topLeft = Offset(w * 0.22f, h * 0.12f),
                size = Size(w * 0.65f, h * 0.55f),
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = darkColor,
                topLeft = Offset(w * 0.22f, h * 0.35f),
                size = Size(w * 0.23f, h * 0.32f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
fun WebErrorFallbackScreen(
    isOnline: Boolean,
    failingUrl: String,
    errorDetail: String?,
    onRetry: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        StokVibeSoftBlue.copy(alpha = 0.6f)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
        ) {
            StokVibeLogo(modifier = Modifier.padding(bottom = 20.dp))

            Text(
                text = if (!isOnline) {
                    stringResource(R.string.error_offline_title)
                } else {
                    stringResource(R.string.error_page_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (!isOnline) {
                    stringResource(R.string.error_offline_desc)
                } else {
                    stringResource(R.string.error_page_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (!errorDetail.isNullOrBlank() || failingUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = listOfNotNull(
                        errorDetail?.takeIf { it.isNotBlank() },
                        failingUrl.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("retry_connection_btn")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_retry), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGoHome,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_login_btn")
            ) {
                Icon(imageVector = Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_open_login), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = remember {
        listOf(
            OnboardingCardData(
                stepLabel = "01 / 04",
                badge = "INSTITUTE COMMAND CENTER",
                title = "All-in-One Institute Workspace",
                description = "StokVibe EDU connects coaching centers, training academies, and schools to manage admissions, batches, fees, and academic operations from any phone.",
                howToUseTitle = "How to get started",
                howToUseSteps = listOf(
                    "Sign in securely with your StokVibe EDU institute account",
                    "Students, batches, notices & records sync live in the cloud"
                ),
                imageRes = R.drawable.img_edu_overview,
                accentColor = StokVibeBlue,
                tintColor = Color(0xFFEFF6FF)
            ),
            OnboardingCardData(
                stepLabel = "02 / 04",
                badge = "STUDENTS & BATCHES",
                title = "Organize Courses, Batches & Attendance",
                description = "Keep complete student profiles, course enrollments, batch schedules, and daily attendance tracking neat and paperless.",
                howToUseTitle = "Smooth batch workflow",
                howToUseSteps = listOf(
                    "Enroll students into active courses and scheduled batches",
                    "Mark daily attendance & review student records in seconds"
                ),
                imageRes = R.drawable.img_edu_students,
                accentColor = StokVibeIndigo,
                tintColor = Color(0xFFEEF2FF)
            ),
            OnboardingCardData(
                stepLabel = "03 / 04",
                badge = "FEES & DIGITAL RECEIPTS",
                title = "Collect Tuition Fees & Track Dues",
                description = "Generate clean digital fee receipts right at the counter and track paid, partial, or upcoming installments without manual ledgers.",
                howToUseTitle = "Fast fee collection",
                howToUseSteps = listOf(
                    "Pick a student or batch to record admission & tuition payments",
                    "Pending dues and installment balances update automatically"
                ),
                imageRes = R.drawable.img_edu_fees,
                accentColor = StokVibeEmerald,
                tintColor = Color(0xFFECFDF5)
            ),
            OnboardingCardData(
                stepLabel = "04 / 04",
                badge = "INSIGHTS & PDF EXPORTS",
                title = "Live Financial Insights & Reports",
                description = "Monitor daily fee collections, staff expenses, and institute growth—then export clean PDF or CSV reports straight to your device.",
                howToUseTitle = "Ready to manage smarter?",
                howToUseSteps = listOf(
                    "View real-time collection summaries on your institute dashboard",
                    "Download fee receipts, attendance & financial PDFs anytime"
                ),
                imageRes = R.drawable.img_edu_reports,
                accentColor = StokVibeCyan,
                tintColor = Color(0xFFECFEFF)
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { cards.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == cards.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF3F7FF),
                        Color.White,
                        Color(0xFFF0F6FF)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.Center)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: StokVibe EDU Brand Header + Skip Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StokVibeNavy,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(7.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRoundRect(
                                    color = Color(0xFF00E5FF),
                                    topLeft = Offset(0f, size.height * 0.38f),
                                    size = Size(size.width * 0.48f, size.height * 0.48f),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = StokVibeBlue,
                                    topLeft = Offset(size.width * 0.34f, size.height * 0.08f),
                                    size = Size(size.width * 0.58f, size.height * 0.58f),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "StokVibe",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = StokVibeNavy
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StokVibeBlue.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "EDU",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = StokVibeBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Institute Guide",
                            style = MaterialTheme.typography.labelSmall,
                            color = StokVibeSlate
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    border = BorderStroke(1.dp, StokVibeBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .clickable { onGetStarted() }
                        .testTag("onboarding_skip_btn")
                ) {
                    Text(
                        text = stringResource(R.string.action_skip),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = StokVibeSlate,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // Continuous swipeable cards pager with gentle scale parallax
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                val scale = 1f - (0.04f * pageOffset)
                val alpha = 1f - (0.25f * pageOffset)

                OnboardingFeatureCard(
                    card = cards[page],
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated continuous pill indicator
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                cards.forEachIndexed { index, card ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 30.dp else 8.dp,
                        animationSpec = spring(),
                        label = "dotWidth"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) card.accentColor else Color(0xFFCBD5E1),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = dotWidth, height = 8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom continuous navigation controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, StokVibeBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = StokVibeNavy
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("onboarding_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous card"
                        )
                    }
                }

                Button(
                    onClick = {
                        if (!isLastPage) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onGetStarted()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastPage) StokVibeNavy else StokVibeBlue,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("onboarding_next_btn")
                ) {
                    Text(
                        text = if (!isLastPage) "Next Feature" else "Continue to Institute Login",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    card: OnboardingCardData,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, card.accentColor.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Step badge & counter header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = card.accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = card.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = card.accentColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = StokVibeSurface,
                        border = BorderStroke(1.dp, StokVibeBorder)
                    ) {
                        Text(
                            text = card.stepLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = StokVibeSlate,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Framed EDU illustration with subtle accent border & bottom blend
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(card.tintColor)
                ) {
                    Image(
                        painter = painterResource(id = card.imageRes),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = card.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = StokVibeNavy
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = card.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StokVibeSlate
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Structured Quick Workflow box inside the card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = card.tintColor.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, card.accentColor.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = card.howToUseTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = StokVibeNavy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    card.howToUseSteps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = card.accentColor,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = StokVibeSlate
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutsTrayContent(
    bookmarks: List<Bookmark>,
    onSelectShortcut: (String) -> Unit,
    onDeleteShortcut: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Saved Shortcuts",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved shortcuts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bookmarks) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectShortcut(item.url) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.url,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (item.id != "1") {
                                IconButton(onClick = { onDeleteShortcut(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove bookmark",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Default view icon",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AlertsDiagnosticsTrayContent(
    notifications: List<NotificationLog>,
    onTriggerMockNotification: (String, String, String, Int) -> Unit,
    onClearLog: () -> Unit,
    onLogTapped: (String?) -> Unit
) {
    var customDelay by remember { mutableFloatStateOf(0f) }
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 550.dp)
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Alerts & Diagnostics",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "1. Test Notification Controls",
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onTriggerMockNotification(
                        "Fee Reminder",
                        "Pending batch fee installment is due.",
                        "FEE_DUE",
                        customDelay.toInt()
                    )
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Fee Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    onTriggerMockNotification(
                        "Batch Sync Updated",
                        "Institute student & attendance records refreshed.",
                        "BATCH_UPDATE",
                        customDelay.toInt()
                    )
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Batch Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Push Delay: ${customDelay.toInt()}s",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(100.dp)
            )
            Slider(
                value = customDelay,
                onValueChange = { customDelay = it },
                valueRange = 0f..15f,
                steps = 3,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "2. Notification Inbox (${notifications.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            if (notifications.isNotEmpty()) {
                Text(
                    text = "Clear Inbox",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { onClearLog() }
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Inbox log empty.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(notifications) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLogTapped(item.targetUrl) },
                        colors = CardDefaults.cardColors(
                            containerColor = when (item.type) {
                                "FEE_DUE", "LOW_STOCK" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                "BATCH_UPDATE", "SIGNAGE_UPDATE" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (item.type) {
                                            "FEE_DUE", "LOW_STOCK" -> Icons.Default.Warning
                                            "BATCH_UPDATE", "SIGNAGE_UPDATE" -> Icons.Default.Refresh
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = "Log type indicator icon",
                                        tint = when (item.type) {
                                            "FEE_DUE", "LOW_STOCK" -> MaterialTheme.colorScheme.error
                                            "BATCH_UPDATE", "SIGNAGE_UPDATE" -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = sdf.format(Date(item.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.text,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
