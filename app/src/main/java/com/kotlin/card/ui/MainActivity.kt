package com.kotlin.card.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.kotlin.card.R
import com.kotlin.card.databinding.ActivityMainBinding
import com.kotlin.card.filter.Utils

class MainActivity : AppCompatActivity(), OnClickListener {

    private lateinit var binding: ActivityMainBinding

    private lateinit var adBannerView : AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater) //initializing the binding class
        setContentView(binding.root)

        MobileAds.initialize(this)
        adBannerView = findViewById(R.id.adView)
        adBannerView.loadAd(AdRequest.Builder().build())

        // Option
        binding.btnOption1.setOnClickListener(this@MainActivity)
        binding.btnOption2.setOnClickListener(this@MainActivity)
        binding.btnOption3.setOnClickListener(this@MainActivity)
        binding.btnOption4.setOnClickListener(this@MainActivity)
        binding.btnOption5.setOnClickListener(this@MainActivity)
        binding.btnOption6.setOnClickListener(this@MainActivity)
        binding.btnOption7.setOnClickListener(this@MainActivity)
        // Number
        binding.btnNumber1.setOnClickListener(this@MainActivity)
        binding.btnNumber2.setOnClickListener(this@MainActivity)
        binding.btnNumber3.setOnClickListener(this@MainActivity)
        binding.btnNumber4.setOnClickListener(this@MainActivity)
        binding.btnNumber5.setOnClickListener(this@MainActivity)
        binding.btnNumber6.setOnClickListener(this@MainActivity)
        binding.btnNumber7.setOnClickListener(this@MainActivity)
        binding.btnNumber8.setOnClickListener(this@MainActivity)
        binding.btnNumber9.setOnClickListener(this@MainActivity)
        binding.btnNumber0.setOnClickListener(this@MainActivity)

        binding.btnSubmit.setOnClickListener {
            accountNumberChecking(
                binding.tilCardNumber.editText?.text.toString(),
                binding.etNumberIgnored.text.toString(),
                binding.etMasked.text.toString()
            )
        }
        // 1-234
        // 1-1234-6756-90
        // 12-234567-33
        // 1234-1236-7654-7890
    }

    @SuppressLint("SetTextI18n")
    private fun accountNumberChecking(cardNumber: String, numberIgnored: String, etMasked: String) {
        if (etMasked.toCharArray().isEmpty()) {
            Toast.makeText(this@MainActivity, "Needed a symbol to represent mask.", Toast.LENGTH_LONG).show()
        } else if (numberIgnored.isEmpty()) {
            Toast.makeText(this@MainActivity, "Please enter a number to prevent to be masked.", Toast.LENGTH_LONG).show()
        } else if (cardNumber.isEmpty()) {
            Toast.makeText(this@MainActivity, "This Credit Card Number cannot be empty.", Toast.LENGTH_LONG).show()
        } else {
            binding.tvOriginalInput.text = cardNumber
            binding.tvResultInput.text = Utils.filterBankNumber(cardNumber, etMasked.toCharArray()[0], '-', numberIgnored.toInt())
        }
    }



    override fun onClick(v: View) {
        when (v.id) {
            (R.id.btnOption1) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("!")
            }

            (R.id.btnOption2) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("@")
            }

            (R.id.btnOption3) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("#")
            }

            (R.id.btnOption4) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("%")
            }

            (R.id.btnOption5) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("^")
            }

            (R.id.btnOption6) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("&")
            }

            (R.id.btnOption7) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("*")
            }
            (R.id.btnOption8) -> {
                binding.etMasked.text.clear()
                binding.etMasked.setText("$")
            }

            (R.id.btnNumber1) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("1")
            }

            (R.id.btnNumber2) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("2")
            }

            (R.id.btnNumber3) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("3")
            }

            (R.id.btnNumber4) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("4")
            }

            (R.id.btnNumber5) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("5")
            }

            (R.id.btnNumber6) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("6")
            }

            (R.id.btnNumber7) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("7")
            }

            (R.id.btnNumber8) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("8")
            }

            (R.id.btnNumber9) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("9")
            }

            (R.id.btnNumber0) -> {
                binding.etNumberIgnored.text.clear()
                binding.etNumberIgnored.setText("0")
            }
        }
    }
}
