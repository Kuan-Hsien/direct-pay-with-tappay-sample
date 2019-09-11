package com.kuanhsien.app.sample.direct_pay_with_tappay_sample

import android.Manifest.permission.READ_PHONE_STATE
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util.*
import kotlinx.android.synthetic.main.activity_main.*
import tech.cherri.tpdirect.api.TPDCard
import tech.cherri.tpdirect.api.TPDServerType
import tech.cherri.tpdirect.api.TPDSetup
import tech.cherri.tpdirect.callback.TPDCardTokenSuccessCallback
import tech.cherri.tpdirect.callback.TPDTokenFailureCallback
import tech.cherri.tpdirect.model.TPDStatus
import tech.cherri.tpdirect.model.Validation.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val tag = this.javaClass.simpleName

    private var tpdCard: TPDCard? = null
    private var cardNumber = emptyString()
    private var expirationMonth = emptyString()
    private var expirationYear = emptyString()
    private var cvc = emptyString()

    val isCardNumberValidLiveData = MutableLiveData<Event<Boolean>>()
    val isExpiryDateValidLiveData = MutableLiveData<Event<Boolean>>()
    val isCvcValidLiveData = MutableLiveData<Event<Boolean>>()
    val isSaveButtonEnabledLiveData by lazy {
        MutableLiveData<Boolean>().apply {
            value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()

        Log.d(tag, "SDK version is " + TPDSetup.getVersion())

        observeData()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions()
        } else {
            startTapPaySetting()
            initCustomCardInputViews()
        }
    }

    private fun setupViews() {
        btn_pay.setOnClickListener(this)
        btn_pay.isEnabled = false

        // For getFraudId
        btn_fraud_id.setOnClickListener(this)

        btn_pay_custom.setOnClickListener(this)
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(applicationContext, READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(tag, "PERMISSION IS ALREADY GRANTED")
            startTapPaySetting()
            initCustomCardInputViews()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(READ_PHONE_STATE), REQUEST_READ_PHONE_STATE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_READ_PHONE_STATE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(tag, "PERMISSION_GRANTED")
                }
                startTapPaySetting()
                initCustomCardInputViews()
            }
            else -> {
            }
        }
    }

    private fun startTapPaySetting() {
        Log.d(tag, "startTapPaySetting")

        // 1. Setup environment.
        TPDSetup.initInstance(
            applicationContext,
            Integer.parseInt(getString(R.string.global_test_app_id)),
            getString(R.string.global_test_app_key),
            TPDServerType.Sandbox
        )

        // 2. Setup input form
        tpdcard_input_form.setTextErrorColor(Color.RED)
        tpdcard_input_form.setOnFormUpdateListener { tpdStatus ->

            tv_tips.text = ""

            when {
                tpdStatus.cardNumberStatus == TPDStatus.STATUS_ERROR -> {
                    tv_tips.text = getString(R.string.ui_tip_invalid_card_num)
                }

                tpdStatus.expirationDateStatus == TPDStatus.STATUS_ERROR -> {
                    tv_tips.text = getString(R.string.ui_tip_invalid_expiration_date)
                }

                tpdStatus.ccvStatus == TPDStatus.STATUS_ERROR -> {
                    tv_tips.text = getString(R.string.ui_tip_invalid_ccv)
                }
            }
            btn_pay.isEnabled = tpdStatus.isCanGetPrime
        }

        // Method 1:
        //  use default tpdForm for user to input payment card info and verify
        tpdCard = TPDCard.setup(tpdcard_input_form)
            .onSuccessCallback(getTPDCardTokenSuccessCallback())
            .onFailureCallback(getTPDTokenFailureCallback())
    }

    fun setCardNumber(numberString: String) {
        cardNumber = numberString.replace(" ", "")
    }

    fun setExpiryDate(dateString: String) {

        val expDates = dateString.split("/")

        expirationMonth = expDates.getOrNull(0)?.toString() ?: emptyString()
        expirationYear = expDates.getOrNull(1)?.toString() ?: emptyString()
    }

    fun setCVC(cvcString: String) {
        cvc = cvcString
    }

    fun validateCardNumber(): Boolean {

        Log.d(tag, "validateCardNumber, number: $cardNumber")
        val isValid = isCardNumberValid(StringBuffer(cardNumber))

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
        val isValid = isDueDateValid(StringBuffer(expirationYear), StringBuffer(expirationMonth))

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
        val isValid = isCCVValid(StringBuffer(cvc))

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

    private fun observeData() {

        isCardNumberValidLiveData.observe(this, EventObserver {
            if (it) {
                tv_card_number.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                text_input_layout_card_number.error = null
            } else {
                tv_card_number.setTextColor(ContextCompat.getColor(this, R.color.scarlet))
                text_input_layout_card_number.error =
                    getString(R.string.hint_invalid_card_number)
            }
        })

        isExpiryDateValidLiveData.observe(this, EventObserver {
            if (it) {
                tv_expiration_date.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                text_input_layout_expiration_date.error = null
            } else {
                tv_expiration_date.setTextColor(ContextCompat.getColor(this, R.color.scarlet))
                text_input_layout_expiration_date.error =
                    getString(R.string.hint_invalid_expiration_date)
            }
        })

        isCvcValidLiveData.observe(this, EventObserver {
            if (it) {
                tv_cvc.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                text_input_layout_cvc.error = null
            } else {
                tv_cvc.setTextColor(ContextCompat.getColor(this, R.color.scarlet))
                text_input_layout_cvc.error = getString(R.string.hint_invalid_cvc)
            }
        })

        isSaveButtonEnabledLiveData.observe(this, Observer {
            btn_pay_custom.isEnabled = it
        })

    }

    private fun initCustomCardInputViews() {

        edittext_card_number.apply {

            afterTextChanged { _, newText, _ ->

                if (newText.length > 19) {

                    setText(StringBuilder(newText).apply {
                        deleteCharAt(length - 1)
                    })

                } else {

                    val stringBuilder = StringBuilder()

                    newText.filter {
                        it.isDigit()
                    }.forEachIndexed { index, char ->

                        // Add blank every 4 digit
                        if (index > 0 && index % 4 == 0) {
                            stringBuilder.append(" ")
                        }
                        stringBuilder.append(char)
                    }

                    if (newText != stringBuilder.toString()) {
                        setText(stringBuilder, TextView.BufferType.EDITABLE)
                    }
                }

                setSelection(text.length)
                setCardNumber(text.toString())

                // Add blank every 4 digit
                if (newText.length >= 19) {
                    validateCardNumber()
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    validateCardNumber()
                }
            }
        }


        edittext_expiration_date.apply {

            afterTextChanged { oldText, newText, isDeleted ->

                if (newText.length > 5) {

                    setText(StringBuilder(newText).apply {
                        deleteCharAt(length - 1)
                    })

                } else {

                    val fixedDateString = if (isDeleted && oldText.endsWith('/') && oldText.trimEnd('/') == newText) {
                        newText.dropLast(1)
                    } else {
                        newText
                    }

                    val stringBuilder = StringBuilder()
                    fixedDateString.filter {
                        it.isDigit()
                    }.forEachIndexed { index, char ->

                        stringBuilder.append(char)

                        if (index == 1) {
                            stringBuilder.append("/")
                        }
                    }

                    if (newText != stringBuilder.toString()) {
                        setText(stringBuilder, TextView.BufferType.EDITABLE)
                    }
                }

                setSelection(text.length)
                setExpiryDate(text.toString())

                if (newText.length >= 5) {
                    validateExpiryDate()
                }

            }

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    validateExpiryDate()
                }
            }
        }

        edittext_cvc.apply {

            afterTextChanged { _, newText, _ ->

                if (newText.length > 3) {
                    setText(StringBuilder(newText).apply {
                        deleteCharAt(length - 1)
                    })
                }

                setSelection(text.length)
                setCVC(text.toString())

                if (newText.length >= 3) {
                    validateCvc()
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    validateCvc()
                }
            }

        }

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_fraud_id -> {
                // GetFraudId for PayByToken
                val fraudId = TPDSetup.getInstance(this).fraudId
                Toast.makeText(this, "FraudId is:$fraudId", Toast.LENGTH_SHORT).show()
            }

            // 4. Calling API for obtaining prime.
            R.id.btn_pay -> {
                Log.d(tag, "onClick btn_pay")

                if (tpdCard != null) {
                    // Method 1:
                    //  use tpdCard.getPrime() to use default tpdForm and get prime
                    (tpdCard as TPDCard).getPrime()

                } else {
                    Log.d(tag, "tpdCard is null")
                }
            }

            // 4. Calling API for obtaining prime.
            R.id.btn_pay_custom -> {

                // TODO check permission if needed
                // Method 2:
                //  use customized layout for user to input payment card info,
                //  and use TPDCard constructor to input those information
                //  it won't check the card information in this situation, just enter onFailure block and show
                //  "Create Token Failed 88004: Parameter Wrong Format"
                //  Reference: https://docs.tappaysdk.com/tutorial/zh/error.html#android-sdk-error-code

                val cardNum = StringBuffer(cardNumber)
                val dueMonth = StringBuffer(expirationMonth)
                val dueYear = StringBuffer(expirationYear)
                val cvc = StringBuffer(cvc)

                tpdCard = TPDCard(
                    tpdcard_input_form.context,
                    cardNum,
                    dueMonth,
                    dueYear,
                    cvc
                )
                    .onSuccessCallback(getTPDCardTokenSuccessCallback())
                    .onFailureCallback(getTPDTokenFailureCallback())

                Log.d(tag, "onClick btn_pay_custom")

                if (tpdCard != null) {
                    // Method 2:
                    //  use tpdCard?.createToken() to use customized input layout
                    tpdCard?.createToken("UNKNOWN")
                } else {
                    Log.d(tag, "tpdCard is null")
                }
            }
        }
    }

    fun getTPDCardTokenSuccessCallback() =
        TPDCardTokenSuccessCallback { token, tpdCardInfo, cardIdentifier ->
            val cardLastFour = tpdCardInfo.lastFour

            Log.d(tag, "[TPDirect createToken] token:  $token")
            Log.d(tag, "[TPDirect createToken] cardLastFour:  $cardLastFour")
            Log.d(tag, "[TPDirect createToken] cardIdentifier:  $cardIdentifier")

            Toast.makeText(this@MainActivity, "Create Token Success", Toast.LENGTH_SHORT).show()

            val resultStr = ("Your prime is $token\n\n" +
                    "Use below cURL to proceed the payment : \n" +
                    ApiUtil.generatePayByPrimeCURLForSandBox(
                        token,
                        getString(R.string.global_test_partnerKey),
                        getString(R.string.global_test_merchant_id)
                    ))

            tv_payment_status.text = resultStr
            Log.d(tag, resultStr)
        }

    fun getTPDTokenFailureCallback() =
        TPDTokenFailureCallback { status, reportMsg ->
            Log.d(tag, "[TPDirect createToken] failure: $status$reportMsg")
            Toast.makeText(
                this@MainActivity,
                "Create Token Failed\n$status: $reportMsg",
                Toast.LENGTH_SHORT
            ).show()
        }

    companion object {
        private const val REQUEST_READ_PHONE_STATE = 101
    }
}
