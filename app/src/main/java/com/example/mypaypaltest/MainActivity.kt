package com.example.mypaypaltest

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.example.mypaypaltest.databinding.ActivityMainBinding
import com.paypal.android.corepayments.CoreConfig
import com.paypal.android.corepayments.Environment
import com.paypal.android.corepayments.PayPalSDKError
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutClient
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutFundingSource
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutListener
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutRequest
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val clientID =
        "ASIa2TcanH6GPmcplQfHyoyG-1_4O4plf9niGGaHtfN-1O0RP8Wv4h8lfvDMHBEi__GrAILMkamq35s7"
    private val secretID =
        "ED3zr41WrB4DqMky_IUvDkfyQ9-FOccEdUx_Mt7zGa75u4_ucVw3oJ7ADWu4aQsZ_nlm12CBWlXesv63"
    private val returnUrl = "com.example.mypaypaltest://demoapp"

    var accessToken = ""
    private lateinit var uniqueId: String
    private var orderid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidNetworking.initialize(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startOrderBtn.visibility = View.GONE

        fetchAccessToken()

        binding.startOrderBtn.setOnClickListener {
            startOrder()
        }

    }

        private fun handlerOrderID(orderID: String) {
        val config = CoreConfig(clientID, environment = Environment.SANDBOX)
        val payPalWebCheckoutClient = PayPalWebCheckoutClient(this@MainActivity, config, returnUrl)
        payPalWebCheckoutClient.listener = object : PayPalWebCheckoutListener {
            override fun onPayPalWebSuccess(result: PayPalWebCheckoutResult) {
                Log.d(TAG, "onPayPalWebSuccess: $result")
            }

            override fun onPayPalWebFailure(error: PayPalSDKError) {
                Log.d(TAG, "onPayPalWebFailure: $error")
            }

            override fun onPayPalWebCanceled() {
                Log.d(TAG, "onPayPalWebCanceled: ")
            }
        }

        orderid = orderID
        val payPalWebCheckoutRequest =
            PayPalWebCheckoutRequest(orderID, fundingSource = PayPalWebCheckoutFundingSource.PAYPAL)
        payPalWebCheckoutClient.start(payPalWebCheckoutRequest)

    }

        private fun startOrder() {
        uniqueId = UUID.randomUUID().toString()

        val orderRequestJson = JSONObject().apply {
            put("intent", "CAPTURE")
            put("purchase_units", JSONArray().apply {
                put(JSONObject().apply {
                    put("reference_id", uniqueId)
                    put("amount", JSONObject().apply {
                        put("currency_code", "USD")
                        put("value", "50.00")
                    })
                })
            })
            put("payment_source", JSONObject().apply {
                put("paypal", JSONObject().apply {
                    put("experience_context", JSONObject().apply {
                        put("payment_method_preference", "IMMEDIATE_PAYMENT_REQUIRED")
                        put("brand_name", "Paypal Integration")
                        put("locale", "en-US")
                        put("landing_page", "LOGIN")
                        put("shipping_preference", "NO_SHIPPING")
                        put("user_action", "PAY_NOW")
                        put("return_url", returnUrl)
                        put("cancel_url", "https://example.com/cancelUrl")
                    })
                })
            })
        }
        AndroidNetworking.post("https://api-m.sandbox.paypal.com/v2/checkout/orders")
            .addHeaders("Authorization", "Bearer $accessToken")
            .addHeaders("Content-Type", "application/json")
            .addHeaders("PayPal-Request-Id", uniqueId)
            .addJSONObjectBody(orderRequestJson)
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject) {
                    Log.d(TAG, "Order Response : $response")
                    handlerOrderID(response.getString("id"))
                }

                override fun onError(error: ANError) {
                    Log.d(
                        TAG,
                        "Order Error : ${error.message} || ${error.errorBody} || ${error.response}"
                    )
                }
            })
    }

        private fun fetchAccessToken() {
            val authString = "$clientID:$secretID"
            val encodedAuthString = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)

            AndroidNetworking.post("https://api-m.sandbox.paypal.com/v1/oauth2/token")
                .addHeaders("Authorization", "Basic $encodedAuthString")
                .addHeaders("Content-Type", "application/x-www-form-urlencoded")
                .addBodyParameter("grant_type", "client_credentials")
                .setPriority(Priority.HIGH)
                .build()
                .getAsJSONObject(object : JSONObjectRequestListener {
                    override fun onResponse(response: JSONObject) {
                        accessToken = response.getString("access_token")
                        Log.d(TAG, accessToken)

                        Toast.makeText(
                            this@MainActivity,
                            "Access Token Fetched!",
                            Toast.LENGTH_SHORT
                        )
                            .show()

                        binding.startOrderBtn.visibility = View.VISIBLE
                    }

                    override fun onError(error: ANError) {
                        Log.d(TAG, error.errorBody)
                        Toast.makeText(this@MainActivity, "Error Occurred!", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: $intent")
        if (intent.data!!.getQueryParameter("opType") == "payment") {
           captureOrder(orderid)
        } else if (intent.data!!.getQueryParameter("opType") == "cancel") {
            Toast.makeText(this, "Payment Cancelled", Toast.LENGTH_SHORT).show()
        }

    }
    private fun captureOrder(orderID: String) {
        AndroidNetworking.post("https://api-m.sandbox.paypal.com/v2/checkout/orders/$orderID/capture")
            .addHeaders("Authorization", "Bearer $accessToken")
            .addHeaders("Content-Type", "application/json")
            .addJSONObjectBody(JSONObject()) // Empty body
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject) {
                    Log.d(TAG, "Capture Response : $response")
                    Toast.makeText(this@MainActivity, "Payment Successful", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: ANError) {
                    // Handle the error
                    Log.e(TAG, "Capture Error : " + error.errorDetail)
                }
            })
    }

        companion object {
            const val TAG = "MyTag"
        }
    }

