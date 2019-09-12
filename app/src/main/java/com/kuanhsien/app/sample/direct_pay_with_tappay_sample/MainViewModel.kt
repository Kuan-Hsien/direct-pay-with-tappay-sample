package com.kuanhsien.app.sample.direct_pay_with_tappay_sample

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util.Event
import com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util.emptyString
import tech.cherri.tpdirect.api.TPDCard
import tech.cherri.tpdirect.api.TPDForm
import tech.cherri.tpdirect.callback.TPDCardTokenSuccessCallback
import tech.cherri.tpdirect.callback.TPDTokenFailureCallback
import tech.cherri.tpdirect.model.Validation

class MainViewModel : ViewModel() {

    private val tag = this.javaClass.simpleName

    private var tpdCard: TPDCard? = null
    var token = emptyString()

    private var cardNumber = emptyString()
    private var expirationMonth = emptyString()
    private var expirationYear = emptyString()
    private var cvc = emptyString()

    // Validation Result
    val isCardNumberValidLiveData = MutableLiveData<Event<Boolean>>()
    val isExpiryDateValidLiveData = MutableLiveData<Event<Boolean>>()
    val isCvcValidLiveData = MutableLiveData<Event<Boolean>>()
    val isSaveButtonEnabledLiveData by lazy {
        MutableLiveData<Boolean>().apply {
            value = false
        }
    }

    // Create Token Result
    val isCreateTokenSuccessLiveData = MutableLiveData<Event<Boolean>>()

    // Notice 1: Different with stripe, need to remove blanks
    fun setCardNumber(numberString: String) {
        cardNumber = numberString.replace(" ", "")
    }

    // Notice 2: Different with stripe, expDates need to use String
    fun setExpiryDate(dateString: String) {
        val expDates = dateString.split("/")

        expirationMonth = expDates.getOrNull(0)?.toString() ?: emptyString()
        expirationYear = expDates.getOrNull(1)?.toString() ?: emptyString()
    }

    fun setCVC(cvcString: String) {
        cvc = cvcString
    }

    // [Validation]
    fun validateCardNumber(): Boolean {

        Log.d(tag, "validateCardNumber, number: $cardNumber")
        val isValid = Validation.isCardNumberValid(StringBuffer(cardNumber))

        if (isValid) {
            isCardNumberValidLiveData.value = Event(true)
        } else {
            isCardNumberValidLiveData.value = Event(false)
        }

        checkButtonState()

        return isValid
    }

    fun validateExpiryDate(): Boolean {

        Log.d(tag, "validateExpiryDate, expireMonth: $expirationMonth, expireYear: $expirationYear")
        val isValid = Validation.isDueDateValid(StringBuffer(expirationYear), StringBuffer(expirationMonth))

        if (isValid) {
            isExpiryDateValidLiveData.value = Event(true)
        } else {
            isExpiryDateValidLiveData.value = Event(false)
        }

        checkButtonState()

        return isValid
    }

    fun validateCvc(): Boolean {

        Log.d(tag, "validateCvc, cvc: $cvc")
        val isValid = Validation.isCCVValid(StringBuffer(cvc))

        if (isValid) {
            isCvcValidLiveData.value = Event(true)
        } else {
            isCvcValidLiveData.value = Event(false)
        }

        checkButtonState()

        return isValid
    }

    private fun checkButtonState() {

        isSaveButtonEnabledLiveData.value =
            (isCardNumberValidLiveData.value?.peekContent() == true)
                    && (isExpiryDateValidLiveData.value?.peekContent() == true)
                    && (isCvcValidLiveData.value?.peekContent() == true)
    }

    // [TPDCard Api]
    // Method 1:
    //  use default tpdForm for user to input payment card info and verify
    // use default TPDForm to enter user's card information
    fun createCardPrime(tpdForm: TPDForm) {
        tpdCard = TPDCard.setup(tpdForm)
            .onSuccessCallback(getTPDCardTokenSuccessCallback())
            .onFailureCallback(getTPDTokenFailureCallback())

        if (tpdCard != null) {
            // Method 1:
            //  use tpdCard.getPrime() to use default tpdForm and get prime
            (tpdCard as TPDCard).getPrime()

        } else {
            Log.d(tag, "tpdCard is null")
        }
    }

    // Method 2:
    //  use customized layout for user to input payment card info,
    //  and use TPDCard constructor to input those information
    //  it won't check the card information in this situation, just enter onFailure block and show
    //  "Create Token Failed 88004: Parameter Wrong Format"
    //  Reference: https://docs.tappaysdk.com/tutorial/zh/error.html#android-sdk-error-code
    fun createCardToken(context: Context) {

        val cardNum = StringBuffer(cardNumber)
        val dueMonth = StringBuffer(expirationMonth)
        val dueYear = StringBuffer(expirationYear)
        val cvc = StringBuffer(cvc)

        tpdCard = TPDCard(
            context,
            cardNum,
            dueMonth,
            dueYear,
            cvc
        )
            .onSuccessCallback(getTPDCardTokenSuccessCallback())
            .onFailureCallback(getTPDTokenFailureCallback())

        if (tpdCard != null) {
            // Method 2:
            //  use tpdCard?.createToken() to use customized input layout
            tpdCard?.createToken("UNKNOWN")
        } else {
            Log.d(tag, "tpdCard is null")
        }
    }

    private fun getTPDCardTokenSuccessCallback() =
        TPDCardTokenSuccessCallback { token, tpdCardInfo, cardIdentifier ->
            val cardLastFour = tpdCardInfo.lastFour

            Log.d(tag, "[TPDirect createToken] token:  $token")
            Log.d(tag, "[TPDirect createToken] cardLastFour:  $cardLastFour")
            Log.d(tag, "[TPDirect createToken] cardIdentifier:  $cardIdentifier")

            isCreateTokenSuccessLiveData.postValue(Event(true))
        }

    private fun getTPDTokenFailureCallback() =
        TPDTokenFailureCallback { status, reportMsg ->
            Log.d(tag, "[TPDirect createToken] failure: $status$reportMsg")

            isCreateTokenSuccessLiveData.postValue(Event(false))
        }



}