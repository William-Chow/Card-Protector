package com.kotlin.card.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.kotlin.card.R
import com.kotlin.card.filter.Utils

class MainActivity : ComponentActivity() {

    private lateinit var mAdView: AdView
    private var mInterstitialAd: InterstitialAd? = null

    private lateinit var inAppUpdate: InAppUpdate
    private var originalInput by mutableStateOf("")
    private var resultInput by mutableStateOf("")
    private val appUpdateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        inAppUpdate.onActivityResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    originalInput = originalInput,
                    resultInput = resultInput,
                    onSubmit = { cardNumber, numberIgnored, masked ->
                        accountNumberChecking(cardNumber, numberIgnored, masked)
                    }
                )
            }
        }

        initAdmob()
        inAppUpdate = InAppUpdate(
            activity = this@MainActivity,
            updateLauncher = appUpdateResultLauncher,
            onUpdateFlowFailed = { }
        )
    }

    private fun initAdmob() {
        MobileAds.initialize(this) { }
    }

    @SuppressLint("SetTextI18n")
    private fun accountNumberChecking(cardNumber: String, numberIgnored: String, masked: String) {
        if (masked.toCharArray().isEmpty()) {
            Toast.makeText(this@MainActivity, "Needed a symbol to represent mask.", Toast.LENGTH_LONG).show()
        } else if (numberIgnored.isEmpty()) {
            Toast.makeText(this@MainActivity, "Please enter a number to prevent to be masked.", Toast.LENGTH_LONG).show()
        } else if (cardNumber.isEmpty()) {
            Toast.makeText(this@MainActivity, "This Credit Card Number cannot be empty.", Toast.LENGTH_LONG).show()
        } else {
            interstitial(
                cardNumber,
                Utils.filterBankNumber(cardNumber, masked.toCharArray()[0], '-', numberIgnored.toInt())
            )
        }
    }

    private fun interstitial(cardNumber: String, filterBankNumber: String) {
        InterstitialAd.load(this@MainActivity, this@MainActivity.getString(R.string.admob_interstitial_ad_unit_id), AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            mInterstitialAd = null
                            originalInput = cardNumber
                            resultInput = filterBankNumber
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            mInterstitialAd = null
                        }
                    }
                    mInterstitialAd!!.show(this@MainActivity)
                }
            }
        )
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
        originalInput: String,
        resultInput: String,
        onSubmit: (String, String, String) -> Unit
    ) {
        val context = LocalContext.current
        var masked by remember { mutableStateOf("") }
        var numberIgnored by remember { mutableStateOf("") }
        var cardNumber by remember { mutableStateOf("") }
        val optionButtons = listOf("!", "@", "#", "%", "^", "&", "*", "$")
        val numberButtonsRow1 = listOf("1", "2", "3", "4", "5")
        val numberButtonsRow2 = listOf("6", "7", "8", "9", "0")

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            TopAppBar(
                title = { Text(text = getString(R.string.app_name)) }
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    AdView(context).also {
                        mAdView = it
                        it.setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                        it.adUnitId = getString(R.string.admob_banner_ad_unit_id)
                        it.loadAd(AdRequest.Builder().build())
                    }
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = getString(R.string.masked), style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    optionButtons.forEach { option ->
                        Button(
                            modifier = Modifier.padding(horizontal = 3.dp),
                            onClick = { masked = option }
                        ) {
                            Text(text = option)
                        }
                    }
                }
                OutlinedTextField(
                    value = masked,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = getString(R.string.e_g_masked_the_value)) },
                    readOnly = true
                )
                Text(text = getString(R.string.number_ignored), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    numberButtonsRow1.forEach { number ->
                        Button(
                            modifier = Modifier.padding(horizontal = 3.dp),
                            onClick = { numberIgnored = number }
                        ) {
                            Text(text = number)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    numberButtonsRow2.forEach { number ->
                        Button(
                            modifier = Modifier.padding(horizontal = 3.dp),
                            onClick = { numberIgnored = number }
                        ) {
                            Text(text = number)
                        }
                    }
                }
                Text(
                    text = getString(R.string.select_0_meaning_all_number_to_be_masked),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = numberIgnored,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = getString(R.string.note_total_number_of_value_to_prevent_be_masked)) },
                    readOnly = true
                )
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() || it == '-' }) {
                            cardNumber = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = getString(R.string.card_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(text = getString(R.string.original_input), style = MaterialTheme.typography.titleSmall)
                Text(text = originalInput, style = MaterialTheme.typography.bodyMedium)
                Text(text = getString(R.string.result_input), style = MaterialTheme.typography.titleSmall)
                Text(text = resultInput, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp),
                onClick = { onSubmit(cardNumber, numberIgnored, masked) }
            ) {
                Text(text = getString(R.string.submit))
            }
        }
    }
}
