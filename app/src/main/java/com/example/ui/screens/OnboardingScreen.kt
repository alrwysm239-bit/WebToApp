package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GlowingFrameCard
import com.example.ui.components.GradientButton
import com.example.ui.components.MintCtaBrush
import com.example.ui.components.PrimaryCtaBrush
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.WebForgeAccent
import com.example.ui.theme.WebForgePrimary
import com.example.ui.theme.WebForgeSuccess
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val titleAr: String,
    val titleEn: String,
    val descAr: String,
    val descEn: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
fun OnboardingScreen(
    isRtl: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val scope = rememberCoroutineScope()

    val slides = listOf(
        OnboardingSlide(
            titleAr = "استوديو HTML المتكامل",
            titleEn = "All-in-One HTML Studio",
            descAr = "اكتب أو الصق كود HTML أو ارفع مشروع ZIP أو استورده من GitHub مع محرر ملون، Emmet، وفحص أخطاء فوري.",
            descEn = "Write, paste, upload ZIP, or import GitHub repos with syntax highlighting, Emmet, and live linting.",
            icon = Icons.Rounded.Code,
            accent = WebForgeAccent,
        ),
        OnboardingSlide(
            titleAr = "معاينة ذكية ومساعد Gemini AI",
            titleEn = "Live Preview & Gemini AI",
            descAr = "أنشئ مواقع كاملة من وصف نصي وعدّلها بالذكاء الاصطناعي مع معاينة الفروقات وإطارات الهاتف والتابلت والكمبيوتر.",
            descEn = "Generate sites from prompts, review AI code diffs, and test interactively across Phone, Tablet, and PC frames.",
            icon = Icons.Rounded.AutoAwesome,
            accent = WebForgePrimary,
        ),
        OnboardingSlide(
            titleAr = "انشر موقعك وحوّله إلى تطبيق",
            titleEn = "Publish Website & Convert to App",
            descAr = "انشر مباشرة على GitHub Pages وNetlify وVercel وCloudflare وFirebase، أو صدّر تطبيق Android وPWA وحزمة المتجر.",
            descEn = "Deploy to 5 cloud hosts with QR sharing, or export Capacitor Android/iOS, Electron, PWA, and Store Packs.",
            icon = Icons.Rounded.RocketLaunch,
            accent = WebForgeSuccess,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF151138),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.webforge_icon_default),
                        contentDescription = strings.appName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.testTag("onboarding_skip_button"),
                ) {
                    Text(strings.onboardingSkip, color = ext.accent)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GlowingFrameCard(
                        modifier = Modifier.size(132.dp),
                        shape = RoundedCornerShape(34.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = slide.icon,
                                contentDescription = null,
                                tint = slide.accent,
                                modifier = Modifier.size(62.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = if (isRtl) slide.titleAr else slide.titleEn,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (isRtl) slide.descAr else slide.descEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(slides.size) { idx ->
                        val active = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (active) 28.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) ext.accent else ext.cardBorder,
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                val isLast = pagerState.currentPage == slides.lastIndex
                GradientButton(
                    text = if (isLast) strings.onboardingStart else strings.onboardingNext,
                    brush = if (isLast) MintCtaBrush else PrimaryCtaBrush,
                    contentColor = if (isLast) Color(0xFF0C0A20) else Color.White,
                    onClick = {
                        if (isLast) {
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "onboarding_primary_button",
                )
            }
        }
    }
}
