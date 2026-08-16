package com.kotlin.card.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.kotlin.card.BuildConfig
import com.kotlin.card.R
import com.kotlin.card.filter.CardTools
import com.kotlin.card.filter.MAX_REVEALED_DIGITS
import com.kotlin.card.filter.MaskMode
import com.kotlin.card.filter.countCards
import com.kotlin.card.filter.extractFirstCardDigits
import com.kotlin.card.filter.keepCounts
import com.kotlin.card.filter.maskAllInText
import com.kotlin.card.filter.maskNumber
import com.kotlin.card.ui.theme.CardGradBottom
import com.kotlin.card.ui.theme.CardGradMid
import com.kotlin.card.ui.theme.CardGradTop
import com.kotlin.card.ui.theme.CardProTheme
import com.kotlin.card.ui.theme.Gold
import com.kotlin.card.ui.theme.OnHeroMuted
import com.kotlin.card.ui.theme.Success
import com.kotlin.card.ui.theme.TextPrimary
import com.kotlin.card.ui.theme.ThemeMode
import com.kotlin.card.ui.theme.successAccent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class ScreenMode { Single, Batch }

// Enums go into saved state as ordinals rather than through the autoSaver's
// Serializable path, which would put a whole class name in the Bundle.
private val ScreenModeSaver =
    Saver<ScreenMode, Int>(save = { it.ordinal }, restore = { ScreenMode.entries[it] })
private val MaskModeSaver =
    Saver<MaskMode, Int>(save = { it.ordinal }, restore = { MaskMode.entries[it] })
private val ThemeModeSaver =
    Saver<ThemeMode, Int>(save = { it.ordinal }, restore = { ThemeMode.entries[it] })

class MainActivity : ComponentActivity() {

    private var mInterstitialAd: InterstitialAd? = null

    /**
     * The share / selection payload, held as state rather than read inline so a
     * second share landing on a live instance (see [onNewIntent]) re-seeds the
     * screen. [shareToken] is bumped on every delivery, which is what lets the
     * composable tell a fresh share apart from one it has already consumed.
     */
    private var sharedText by mutableStateOf<String?>(null)
    private var shareToken by mutableStateOf(0)

    private lateinit var inAppUpdate: InAppUpdate
    private val appUpdateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        inAppUpdate.onActivityResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeSharedText(intent)
        setContent {
            var themeMode by rememberSaveable(stateSaver = ThemeModeSaver) {
                mutableStateOf(ThemeMode.System)
            }
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            CardProTheme(darkTheme = darkTheme) {
                MainScreen(
                    sharedText = sharedText,
                    shareToken = shareToken,
                    onCommit = { showInterstitial() },
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it }
                )
            }
        }

        MobileAds.initialize(this) { loadInterstitial() }
        inAppUpdate = InAppUpdate(
            activity = this@MainActivity,
            updateLauncher = appUpdateResultLauncher,
            onUpdateFlowFailed = { }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedText(intent)
    }

    /**
     * Lift the payload out of [source] and hand it to the UI exactly once.
     *
     * The Activity's own `intent` is then replaced with an empty one: it holds
     * an unmasked card number otherwise, and a recreated instance re-reads that
     * same field. Nothing leaves the device either way — this only shortens how
     * long the plaintext sits in a field we control.
     */
    private fun consumeSharedText(source: Intent) {
        val text = parseSharedText(source) ?: return
        sharedText = text
        shareToken++
        intent = Intent()
    }

    /** Text handed to us by a SEND share or the PROCESS_TEXT selection action. */
    private fun parseSharedText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND ->
            if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> null
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            this@MainActivity,
            BuildConfig.ADMOB_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                }
            }
        )
    }

    /**
     * Show a pre-loaded interstitial if one is ready, then reload for next time.
     * The masked result is computed live and never depends on this — a missing or
     * failed ad simply shows nothing extra instead of blocking the output.
     */
    private fun showInterstitial() {
        val ad = mInterstitialAd
        if (ad == null) {
            loadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                mInterstitialAd = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                mInterstitialAd = null
                loadInterstitial()
            }
        }
        ad.show(this@MainActivity)
    }

    override fun onResume() {
        super.onResume()
        inAppUpdate.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppUpdate.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(
        sharedText: String?,
        shareToken: Int,
        onCommit: () -> Unit,
        themeMode: ThemeMode,
        onThemeChange: (ThemeMode) -> Unit
    ) {
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val haptic = LocalHapticFeedback.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val colors = MaterialTheme.colorScheme

        // Everything the user typed or chose goes through rememberSaveable, so a
        // theme flip or a rotation — both of which destroy and recreate the
        // Activity — no longer wipes the screen. `remember` survives
        // recomposition only; saved state is what survives recreation.
        //
        // Privacy note: this puts the card number into the saved-instance-state
        // Bundle, which crosses a Binder into system_server. It is not written to
        // disk (persistableMode defaults to persistRootOnly, which persists only
        // the launch Intent) and is not covered by allowBackup. It never leaves
        // the device, so the "On-device" badge still holds.
        var screenMode by rememberSaveable(stateSaver = ScreenModeSaver) {
            mutableStateOf(ScreenMode.Single)
        }
        var cardNumber by rememberSaveable { mutableStateOf("") }
        var batchText by rememberSaveable { mutableStateOf("") }
        var maskMode by rememberSaveable(stateSaver = MaskModeSaver) { mutableStateOf(MaskMode.LAST) }
        var keepN by rememberSaveable { mutableStateOf(4) }
        var maskSymbolIndex by rememberSaveable { mutableStateOf(0) }
        // Deliberately NOT saveable: it only drives the hero's bounce animation,
        // and restoring it would replay the bounce on every theme flip.
        var commitPulse by remember { mutableStateOf(0) }
        var consumedShareToken by rememberSaveable { mutableStateOf(0) }

        val symbols = listOf('*', '•', '#', 'x', '$', '!', '@', '%', '^', '&')
        val maskSymbol = symbols[maskSymbolIndex.coerceIn(symbols.indices)]
        val (keepLeading, keepTrailing) = keepCounts(maskMode, keepN.coerceIn(0, MAX_REVEALED_DIGITS))

        // Seed from a share exactly once per delivery. This runs as an effect
        // rather than in the state initializers above on purpose: initializers
        // are skipped on restore, so seeding there worked, but a share that had
        // been edited and then survived a recreation would quietly get the
        // original shared value written back over the edit. Gating on a token
        // that itself lives in saved state removes that path entirely.
        LaunchedEffect(shareToken) {
            val text = sharedText
            if (text == null || shareToken == consumedShareToken) return@LaunchedEffect
            consumedShareToken = shareToken
            if (countCards(text) > 1) {
                screenMode = ScreenMode.Batch
                batchText = text
            } else {
                screenMode = ScreenMode.Single
                cardNumber = extractFirstCardDigits(text)
            }
        }

        val singleMasked = remember(cardNumber, maskSymbol, keepLeading, keepTrailing) {
            maskNumber(cardNumber, maskSymbol, keepLeading, keepTrailing)
        }
        val batchMasked = remember(batchText, maskSymbol, keepLeading, keepTrailing) {
            maskAllInText(batchText, maskSymbol, keepLeading, keepTrailing)
        }
        val batchCount = remember(batchText) { countCards(batchText) }

        val hasInput = cardNumber.isNotEmpty()
        val brand = CardTools.detectBrand(cardNumber)
        val luhnOk = hasInput && CardTools.isLuhnValid(cardNumber)
        val canCommit = if (screenMode == ScreenMode.Single) hasInput else batchCount > 0

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = colors.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // ── Slim header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onBackground
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "On-device",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.successAccent,
                            modifier = Modifier
                                .border(1.dp, colors.successAccent, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        ThemeMenu(themeMode = themeMode, onThemeChange = onThemeChange)
                    }
                }

                // ── Fenced banner ad slot ────────────────────────────────────
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = BuildConfig.ADMOB_BANNER
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )

                // ── Single / Batch mode tabs ─────────────────────────────────
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    SegmentedButton(
                        selected = screenMode == ScreenMode.Single,
                        onClick = { screenMode = ScreenMode.Single },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Single card") }
                    SegmentedButton(
                        selected = screenMode == ScreenMode.Batch,
                        onClick = { screenMode = ScreenMode.Batch },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Batch text") }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    if (screenMode == ScreenMode.Single) {
                        // ── Hero ─────────────────────────────────────────────
                        CardHero(
                            masked = singleMasked,
                            brand = brand,
                            luhnOk = luhnOk,
                            hasInput = hasInput,
                            pulse = commitPulse
                        )

                        Spacer(Modifier.height(14.dp))

                        // ── Action row ───────────────────────────────────────
                        val actionPadding = PaddingValues(horizontal = 4.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = hasInput,
                                contentPadding = actionPadding,
                                onClick = {
                                    clipboard.setText(AnnotatedString(singleMasked))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch { snackbarHostState.showSnackbar("Copied masked card") }
                                }
                            ) { Text("Copy", maxLines = 1, softWrap = false) }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = hasInput,
                                contentPadding = actionPadding,
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, singleMasked)
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                }
                            ) { Text("Share", maxLines = 1, softWrap = false) }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = hasInput,
                                contentPadding = actionPadding,
                                onClick = {
                                    scope.launch {
                                        val bitmap = renderCardBitmap(singleMasked, brand)
                                        shareCardImage(context, bitmap)
                                    }
                                }
                            ) { Text("Image", maxLines = 1, softWrap = false) }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = hasInput,
                                contentPadding = actionPadding,
                                onClick = {
                                    cardNumber = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            ) { Text("Clear", maxLines = 1, softWrap = false) }
                        }

                        Spacer(Modifier.height(14.dp))

                        // ── Card number input ────────────────────────────────
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = cardNumber,
                                    onValueChange = { value ->
                                        val onlyDigits = value.filter { it.isDigit() }
                                        if (onlyDigits.length <= 19) cardNumber = onlyDigits
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(getString(R.string.card_number)) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                    visualTransformation = CardGroupingTransformation,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    } else {
                        // ── Batch text ───────────────────────────────────────
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = batchText,
                                    onValueChange = { batchText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Paste text with card numbers") },
                                    minLines = 4,
                                    maxLines = 8,
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "$batchCount card number(s) detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (batchCount > 0) colors.successAccent else colors.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Result",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = batchMasked.ifEmpty { "Masked text appears here" },
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (batchMasked.isEmpty()) colors.onSurfaceVariant else colors.onSurface
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = batchCount > 0,
                                    onClick = {
                                        clipboard.setText(AnnotatedString(batchMasked))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch { snackbarHostState.showSnackbar("Copied masked text") }
                                    }
                                ) { Text("Copy result") }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Shared masking settings ──────────────────────────────
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Reveal",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val modes = listOf(
                                    "Last" to MaskMode.LAST,
                                    "First" to MaskMode.FIRST,
                                    "6 + 4" to MaskMode.FIRST6_LAST4
                                )
                                modes.forEachIndexed { index, (label, mode) ->
                                    SegmentedButton(
                                        selected = maskMode == mode,
                                        onClick = {
                                            maskMode = mode
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                                    ) { Text(label) }
                                }
                            }
                            if (maskMode != MaskMode.FIRST6_LAST4) {
                                // Ceiling matches the masking clamp, so the readout
                                // below never promises more than the mask reveals.
                                Slider(
                                    value = keepN.toFloat(),
                                    onValueChange = { keepN = it.roundToInt() },
                                    valueRange = 0f..MAX_REVEALED_DIGITS.toFloat(),
                                    steps = MAX_REVEALED_DIGITS - 1
                                )
                            }
                            Text(
                                text = when (maskMode) {
                                    MaskMode.FIRST6_LAST4 -> "Showing first 6 + last 4"
                                    MaskMode.LAST ->
                                        if (keepN == 0) "Masking every digit" else "Keeping last $keepN"
                                    MaskMode.FIRST ->
                                        if (keepN == 0) "Masking every digit" else "Keeping first $keepN"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = getString(R.string.masked),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                symbols.forEachIndexed { index, symbol ->
                                    FilterChip(
                                        selected = maskSymbolIndex == index,
                                        onClick = {
                                            maskSymbolIndex = index
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        label = { Text(symbol.toString()) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // ── Primary CTA ──────────────────────────────────────────────
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .imePadding(),
                    enabled = canCommit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Success,
                        contentColor = colors.onSecondary
                    ),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        commitPulse++
                        if (screenMode == ScreenMode.Batch) {
                            clipboard.setText(AnnotatedString(batchMasked))
                            scope.launch { snackbarHostState.showSnackbar("Copied masked text") }
                        }
                        onCommit()
                    }
                ) {
                    Text(
                        text = if (screenMode == ScreenMode.Single) "Mask card" else "Copy masked text"
                    )
                }
            }
        }
    }
}

/**
 * The masked result rendered as the visual hero: a credit-card mockup whose
 * kept (revealed) trailing digits glow mint while masked glyphs stay muted.
 */
@Composable
private fun CardHero(
    masked: String,
    brand: String,
    luhnOk: Boolean,
    hasInput: Boolean,
    pulse: Int
) {
    // Fixed, not colorScheme-derived: the hero is dark in both themes.
    val muted = OnHeroMuted
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            scale.snapTo(0.96f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(CardGradTop, CardGradMid, CardGradBottom))
            )
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Gold)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = brand,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (luhnOk) {
                        Text(
                            text = "✓ valid",
                            color = Success,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (hasInput) {
                Text(
                    text = buildHeroNumber(masked, muted),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = "•••• •••• •••• ••••",
                    color = muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (hasInput) "Masked on this device" else "Your masked card appears here",
                color = muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Two-tone the masked string in a single annotated string: kept digits in mint,
 * mask glyphs muted. Separators are dropped and the value is regrouped into 4s.
 */
private fun buildHeroNumber(masked: String, mutedColor: Color): AnnotatedString {
    val compact = masked.filter { it != '-' && it != ' ' }
    return buildAnnotatedString {
        compact.forEachIndexed { index, c ->
            if (index != 0 && index % 4 == 0) {
                withStyle(SpanStyle(color = mutedColor.copy(alpha = 0.4f))) { append(' ') }
            }
            if (c.isDigit()) {
                withStyle(SpanStyle(color = Success, fontWeight = FontWeight.SemiBold)) { append(c) }
            } else {
                withStyle(SpanStyle(color = mutedColor)) { append(c) }
            }
        }
    }
}

/** Header overflow menu for switching between System / Light / Dark themes. */
@Composable
private fun ThemeMenu(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Text(
            text = "⋮",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options = listOf(
                "System" to ThemeMode.System,
                "Light" to ThemeMode.Light,
                "Dark" to ThemeMode.Dark
            )
            options.forEach { (label, mode) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onThemeChange(mode)
                        expanded = false
                    },
                    trailingIcon = {
                        if (themeMode == mode) Text(text = "✓", color = colors.primary)
                    }
                )
            }
        }
    }
}

/**
 * Displays a digit string grouped into 4-4-4-4 blocks while keeping the raw,
 * digits-only value intact. The offset mapping accounts for the inserted spaces
 * so the cursor stays put while editing.
 */
private object CardGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val grouped = buildString {
            digits.forEachIndexed { index, c ->
                if (index != 0 && index % 4 == 0) append(' ')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                return offset + (offset - 1) / 4
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                return offset - offset / 5
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
