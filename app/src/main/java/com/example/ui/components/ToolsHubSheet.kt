package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditReport
import com.example.domain.WebPConversionResult
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubSheet(
    isRtl: Boolean,
    auditReport: AuditReport?,
    lastWebPResult: WebPConversionResult?,
    onRunAudit: () -> Unit,
    onPickImageForWebP: (quality: Int) -> Unit,
    onAddWebPToAssets: () -> Unit,
    onExportIconSplashPack: (saveToDevice: Boolean) -> Unit,
    onExportSingleHtml: (saveToDevice: Boolean) -> Unit,
    onInjectTrackingAndCookies: (gaId: String, adsenseId: String, includeBanner: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var webpQuality by remember { mutableIntStateOf(80) }
    var gaId by remember { mutableStateOf("G-") }
    var adsenseId by remember { mutableStateOf("") }
    var includeCookieBanner by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(strings.toolsHubTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // 1. Comprehensive 5-Pillar Site Audit
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Assessment, null, tint = ext.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.auditTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientButton(
                        text = strings.runAudit,
                        icon = Icons.Rounded.Assessment,
                        onClick = onRunAudit,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "run_site_audit_button",
                    )

                    if (auditReport != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Score: ${auditReport.overallScore}/100",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ext.success,
                            )
                            Surface(
                                color = ext.success.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = "Grade ${auditReport.grade}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ext.success,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AuditBar("Performance", auditReport.performanceScore, ext.accent)
                        AuditBar("SEO", auditReport.seoScore, ext.softViolet)
                        AuditBar("Accessibility", auditReport.accessibilityScore, ext.success)
                        AuditBar("Security", auditReport.securityScore, ext.warning)
                        AuditBar("Links", auditReport.linksScore, ext.accent)
                        Spacer(Modifier.height(6.dp))
                        auditReport.findings.forEach { f ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (f.passed) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (f.passed) ext.success else ext.warning,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${if (isRtl) f.titleAr else f.titleEn}: ${if (isRtl) f.detailAr else f.detailEn}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // 2. WebP Image Compressor
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Compress, null, tint = ext.success)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isRtl) "ضغط الصور إلى WebP" else "Image to WebP Compressor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Quality: $webpQuality%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = webpQuality.toFloat(),
                        onValueChange = { webpQuality = it.roundToInt() },
                        valueRange = 20f..100f,
                    )
                    OutlinedGlowButton(
                        text = if (isRtl) "اختيار صورة وضغطها إلى WebP" else "Pick Image & Compress to WebP",
                        icon = Icons.Rounded.Compress,
                        onClick = { onPickImageForWebP(webpQuality) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (lastWebPResult != null) {
                        Spacer(Modifier.height(8.dp))
                        val saved = ((lastWebPResult.originalBytes - lastWebPResult.compressedBytes).coerceAtLeast(0) * 100) /
                            lastWebPResult.originalBytes.coerceAtLeast(1)
                        Text(
                            text = "${lastWebPResult.fileName}: ${lastWebPResult.originalBytes / 1024}KB → ${lastWebPResult.compressedBytes / 1024}KB (-$saved%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.success,
                        )
                        Spacer(Modifier.height(6.dp))
                        GradientButton(
                            text = if (isRtl) "إضافة لملفات المشروع" else "Add to Project Assets",
                            brush = MintCtaBrush,
                            contentColor = Color(0xFF0C0A20),
                            onClick = onAddWebPToAssets,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 3. Icon & Splash Pack + Single Self-Contained HTML
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedGlowButton(
                    text = if (isRtl) "حزمة الأيقونات" else "Icon Pack ZIP",
                    icon = Icons.Rounded.Collections,
                    onClick = { onExportIconSplashPack(true) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedGlowButton(
                    text = strings.singleHtmlTitle.take(16),
                    icon = Icons.Rounded.Code,
                    onClick = { onExportSingleHtml(true) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 4. Analytics (GA4) + AdSense + Cookie Consent Injector
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cookie, null, tint = ext.warning)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isRtl) "تضمين GA4 / AdSense وإشعار الكوكيز" else "Inject GA4 / AdSense & Cookie Banner",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedTextField(
                        value = gaId,
                        onValueChange = { gaId = it },
                        label = { Text("Google Analytics GA4 ID (G-XXXXXXXX)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = adsenseId,
                        onValueChange = { adsenseId = it },
                        label = { Text("AdSense Publisher ID (ca-pub-...)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeCookieBanner, onCheckedChange = { includeCookieBanner = it })
                        Text(if (isRtl) "إضافة شريط موافقة الكوكيز (GDPR)" else "Add GDPR Cookie Consent Banner")
                    }
                    GradientButton(
                        text = if (isRtl) "تضمين في الصفحة" else "Inject into HTML",
                        onClick = { onInjectTrackingAndCookies(gaId, adsenseId, includeCookieBanner) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AuditBar(label: String, score: Int, color: Color) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text("$score%", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            color = color,
            modifier = Modifier.fillMaxWidth().height(5.dp),
        )
    }
}
