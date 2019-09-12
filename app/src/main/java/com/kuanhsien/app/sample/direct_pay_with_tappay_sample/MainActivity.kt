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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util.ApiUtil
import com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util.EventObserver
import com.kuanhsien.app.sample.direct_pay_with_tappay_sample.util.afterTextChanged
import kotlinx.android.synthetic.main.activity_main.*
import tech.cherri.tpdirect.api.TPDServerType
import tech.cherri.tpdirect.api.TPDSetup
import tech.cherri.tpdirect.model.TPDStatus

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val tag = this.javaClass.simpleName

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()

        Log.d(tag, "SDK version is " + TPDSetup.getVersion())

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
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
    }

    private fun observeData() {

        with(viewModel) {

            isCardNumberValidLiveData.observe(this@MainActivity, EventObserver {
                if (it) {
                    tv_card_number.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorPrimaryDark))
                    text_input_layout_card_number.error = null
                } else {
                    tv_card_number.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.scarlet))
                    text_input_layout_card_number.error =
                        getString(R.string.hint_invalid_card_number)
                }
            })

            isExpiryDateValidLiveData.observe(this@MainActivity, EventObserver {
                if (it) {
                    tv_expiration_date.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorPrimaryDark))
                    text_input_layout_expiration_date.error = null
                } else {
                    tv_expiration_date.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.scarlet))
                    text_input_layout_expiration_date.error =
                        getString(R.string.hint_invalid_expiration_date)
                }
            })

            isCvcValidLiveData.observe(this@MainActivity, EventObserver {
                if (it) {
                    tv_cvc.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorPrimaryDark))
                    text_input_layout_cvc.error = null
                } else {
                    tv_cvc.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.scarlet))
                    text_input_layout_cvc.error = getString(R.string.hint_invalid_cvc)
                }
            })

            isSaveButtonEnabledLiveData.observe(this@MainActivity, Observer {
                btn_pay_custom.isEnabled = it
            })

            isCreateTokenSuccessLiveData.observe(this@MainActivity, EventObserver {
                if (it) {
                    showCreateTokenSuccess(token)
                } else {
                    showCreateTokenFail()
                }
            })
        }
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
                viewModel.setCardNumber(text.toString())

                // Add blank every 4 digit
                if (newText.length >= 19) {
                    viewModel.validateCardNumber()
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    viewModel.validateCardNumber()
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
                viewModel.setExpiryDate(text.toString())

                if (newText.length >= 5) {
                    viewModel.validateExpiryDate()
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    viewModel.validateExpiryDate()
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
                viewModel.setCVC(text.toString())

                if (newText.length >= 3) {
                    viewModel.validateCvc()
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    viewModel.validateCvc()
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
                viewModel.createCardPrime(tpdcard_input_form)
            }

            // 4. Calling API for obtaining prime.
            R.id.btn_pay_custom -> {
                Log.d(tag, "onClick btn_pay_custom")

                // TODO check permission if needed
                viewModel.createCardToken(this)
            }
        }
    }

    private fun showCreateTokenSuccess(token: String) {

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

    private fun showCreateTokenFail() {
        Toast.makeText(this@MainActivity, "Create Token Failed", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_READ_PHONE_STATE = 101
    }
}
