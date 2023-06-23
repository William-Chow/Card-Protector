package com.kotlin.card.filter

class Utils {

    companion object {
        fun filterBankNumber(_accountNumber: String, _masked: Char, _ignoredValue: Char = '-', _numberIgnored: Int): String {
            // Remove all '-' value to find the number enough to mask or not
            val numberOfValue = _accountNumber.replace("-", "")
            // Check without '-' value is enough to mask or not. IF no enough then no need mask and maybe print error/print same result
            if (numberOfValue.length < _numberIgnored) return "$_accountNumber \n\nUnable to Masked due to limit of Card Number"

            // Reverse it so can ignored the value to mask first
            val value = _accountNumber.reversed()
            var numberIgnored = _numberIgnored
            var appendAllValue = ""
            for (c in value) {
                var appendValue: Char
                if (c == _ignoredValue) {
                    appendValue = _ignoredValue
                } else {
                    if (numberIgnored != 0) {
                        numberIgnored -= 1
                        appendValue = c
                    } else {
                        appendValue = _masked
                    }
                }
                appendAllValue = appendAllValue.plus(appendValue)
            }
            return appendAllValue.reversed()
        }
    }
}