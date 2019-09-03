package com.kuanhsien.app.sample.direct_pay_with_tappay_sample

import android.Manifest.permission.READ_PHONE_STATE
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import tech.cherri.tpdirect.api.TPDCard
import tech.cherri.tpdirect.api.TPDForm
import tech.cherri.tpdirect.api.TPDServerType
import tech.cherri.tpdirect.api.TPDSetup
import tech.cherri.tpdirect.callback.TPDCardTokenSuccessCallback
import tech.cherri.tpdirect.callback.TPDTokenFailureCallback
import tech.cherri.tpdirect.model.TPDStatus


class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val tag = this.javaClass.simpleName

    private var tpdForm: TPDForm? = null
    private var tpdCard: TPDCard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()

        Log.d(tag, "SDK version is " + TPDSetup.getVersion())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions()
        } else {
            startTapPaySetting()
        }
    }

    private fun setupViews() {
        btn_pay.setOnClickListener(this)
        btn_pay.isEnabled = false

        // For getFraudId
        btn_fraud_id.setOnClickListener(this)
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(applicationContext, READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(tag, "PERMISSION IS ALREADY GRANTED")
            startTapPaySetting()
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
        tpdForm = findViewById<TPDForm>(R.id.tpdcard_input_form)
        tpdForm?.setTextErrorColor(Color.RED)
        tpdForm?.setOnFormUpdateListener { tpdStatus ->

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


        // 3. Setup TPDCard with form and callbacks.
        val tpdTokenSuccessCallback =
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

        val tpdTokenFailureCallback = TPDTokenFailureCallback { status, reportMsg ->
            Log.d("TPDirect createToken", "failure: $status$reportMsg")
            Toast.makeText(
                this@MainActivity,
                "Create Token Failed\n$status: $reportMsg",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Method 1:
        //  use default tpdForm for user to input payment card info and verify
        tpdCard = TPDCard.setup(tpdForm)
            .onSuccessCallback(tpdTokenSuccessCallback)
            .onFailureCallback(tpdTokenFailureCallback)

        // Method 2:
        //  use customized layout for user to input payment card info,
        //  and use TPDCard constructor to input those information
        //  it won't check the card information in this situation, just enter onFailure block and show
        //  "Create Token Failed 88004: Parameter Wrong Format"
        //  Reference: https://docs.tappaysdk.com/tutorial/zh/error.html#android-sdk-error-code
//        tpdCard = TPDCard(
//            tpdForm?.context,
//            StringBuffer("4242424242424242"),
//            StringBuffer("01"),
//            StringBuffer("23"),
//            StringBuffer("123")
//        )
//            .onSuccessCallback(tpdTokenSuccessCallback)
//            .onFailureCallback(tpdTokenFailureCallback)
    }


    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_fraud_id -> {
                // GetFraudId for PayByToken
                val fraudId = TPDSetup.getInstance(this).fraudId
                Toast.makeText(this, "FraudId is:$fraudId", Toast.LENGTH_SHORT).show()
            }

            R.id.btn_pay ->
                // 4. Calling API for obtaining prime.
                if (tpdCard != null) {
                    // Method 1:
                    //  use tpdCard.getPrime() to use default tpdForm and get prime
                    (tpdCard as TPDCard).getPrime()

                    // Method 2:
                    //  use tpdCard?.createToken() to use customized input layout
//                    tpdCard?.createToken("UNKNOWN")
                } else {
                    Log.d(tag, "onClick btn_pay")
                }
        }
    }

    companion object {
        private const val REQUEST_READ_PHONE_STATE = 101
    }


}
