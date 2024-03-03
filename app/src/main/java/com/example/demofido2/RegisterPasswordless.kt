package com.example.demofido2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import org.json.JSONObject
import java.lang.Exception
import java.util.concurrent.Executors
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.*
import okhttp3.ResponseBody
import org.json.JSONArray
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterPasswordless: ComponentActivity() {

    private lateinit var btnRegPasswordless: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("accessToken")){
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }else{
            setContentView(R.layout.reg_passwordless)
            btnRegPasswordless = findViewById(R.id.btnRegPasswordless);
            btnRegPasswordless.setOnClickListener {

            }
        }
    }
    //**********************************************************************************************************//
    //******************************* FIDO2 Registration Step 1 ************************************************//
    //******************************* Get challenge from the Server ********************************************//
    //**********************************************************************************************************//
    private fun fido2RegisterInitiate() {

        val result = JSONObject()
        val mediaType = "application/json".toMediaTypeOrNull()

        result.put("username", usernameButton.text.toString())

        //Optional
        val jsonObject = JSONObject()
        //jsonObject.put("authenticatorAttachment","platform")
        jsonObject.put("userVerification", "required")
        result.put("authenticatorSelection", jsonObject)

        val requestBody = RequestBody.create(mediaType, result.toString())

        try {
            RPApiService.getApi().registerInitiate(requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {

                            var obj = JSONObject(response.body()?.string())
                            var intiateResponse = obj.getJSONObject("initiateRegistrationResponse")
                            val c = intiateResponse?.getString("challenge")
                            val challenge = Base64.decode(c!!, BASE64_FLAG)
                            var rpname = intiateResponse?.getJSONObject("rp")!!.getString("name")
                            var username =
                                intiateResponse?.getJSONObject("user")!!.getString("name")
                            var userId = intiateResponse?.getJSONObject("user")!!.getString("id")

                            var authenticatorAttachement = ""
                            if (intiateResponse.has("authenticatorSelection")) {
                                if (intiateResponse?.getJSONObject("authenticatorSelection")
                                        .has("authenticatorAttachment")
                                ) {
                                    authenticatorAttachement =
                                        intiateResponse?.getJSONObject("authenticatorSelection")
                                            ?.getString("authenticatorAttachment")!!
                                    Log.d(
                                        LOG_TAG,
                                        "authenticatorAttachement $authenticatorAttachement"
                                    )
                                }
                            }

                            val attestation = intiateResponse?.getString("attestation")
                            Log.d(LOG_TAG, attestation)
                            var attestationPreference: AttestationConveyancePreference =
                                AttestationConveyancePreference.NONE
                            if (attestation == "direct") {
                                attestationPreference = AttestationConveyancePreference.DIRECT
                            } else if (attestation == "indirect") {
                                attestationPreference = AttestationConveyancePreference.INDIRECT
                            } else if (attestation == "none") {
                                attestationPreference = AttestationConveyancePreference.NONE
                            }

                            fido2AndroidRegister(
                                rpname,
                                challenge,
                                userId,
                                username,
                                authenticatorAttachement,
                                attestationPreference
                            )
                        } else {
                            resultText.text = response.toString()
                        }

                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d(LOG_TAG, t.message)
                        resultText.text = t.message
                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    //**********************************************************************************************************//
    //******************************* FIDO2 Registration Step 2 ************************************************//
    //******************************* Invoke Android FIDO2 API  ************************************************//
    //**********************************************************************************************************//

    private fun fido2AndroidRegister(
        rpname: String,
        challenge: ByteArray,
        userId: String,
        userName: String?,
        authenticatorAttachment: String?,
        attestationPreference: AttestationConveyancePreference
    ) {

        try {
            val options = PublicKeyCredentialCreationOptions.Builder()
                .setAttestationConveyancePreference(attestationPreference)
                .setRp(PublicKeyCredentialRpEntity(RPID, rpname, null))
                .setUser(
                    PublicKeyCredentialUserEntity(
                        userId.toByteArray(),
                        userId,
                        null,
                        userName
                    )
                )
                .setChallenge(challenge)
                .setParameters(
                    listOf(
                        PublicKeyCredentialParameters(
                            PublicKeyCredentialType.PUBLIC_KEY.toString(),
                            EC2Algorithm.ES256.algoValue
                        )
                    )
                )

            if (authenticatorAttachment != "") {
                val builder = AuthenticatorSelectionCriteria.Builder()
                builder.setAttachment(Attachment.fromString("platform"))
                options.setAuthenticatorSelection(builder.build())
            }

            val fido2ApiClient = Fido.getFido2ApiClient(applicationContext)
            val fido2PendingIntentTask = fido2ApiClient.getRegisterIntent(options.build())
            fido2PendingIntentTask.addOnSuccessListener { fido2PendingIntent ->
                if (fido2PendingIntent.hasPendingIntent()) {
                    try {
                        Log.d(LOG_TAG, "launching Fido2 Pending Intent")
                        fido2PendingIntent.launchPendingIntent(
                            this@MainActivity,
                            REQUEST_CODE_REGISTER
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }


    //**********************************************************************************************************//
    //******************************* FIDO2 Registration Step 3 ************************************************//
    //***************************** Send Signed Challenge (Attestation) to the Server for validation ***********//
    //**********************************************************************************************************//
    private fun fido2RegisterComplete(fido2Response: ByteArray) {
        val attestationResponse =
            AuthenticatorAttestationResponse.deserializeFromBytes(fido2Response)
        val credId = Base64.encodeToString(attestationResponse.keyHandle, BASE64_FLAG)
        val clientDataJson = Base64.encodeToString(attestationResponse.clientDataJSON, BASE64_FLAG)
        val attestationObjectBase64 =
            Base64.encodeToString(attestationResponse.attestationObject, Base64.DEFAULT)

        val webAuthnResponse = JSONObject()
        val response = JSONObject()

        response.put("attestationObject", attestationObjectBase64)
        response.put("clientDataJSON", clientDataJson)


        webAuthnResponse.put("type", "public-key")
        webAuthnResponse.put("id", credId)
        webAuthnResponse.put("rawId", credId)
        webAuthnResponse.put("getClientExtensionResults", JSONObject())
        webAuthnResponse.put("response", response)

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, webAuthnResponse.toString())

        try {
            RPApiService.getApi()
                .registerComplete("username=${usernameButton.text.toString()}", requestBody)
                //.registerComplete( requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            resultText.text = "Registration Successful"
                            Log.d("response", response.message())
                        } else {
                            resultText.text = "Registration Failed" + "\n" + response.toString()
                            Log.d("response", response.errorBody().toString())
                        }

                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("response", t.message)

                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    //**********************************************************************************************************//
    //******************************* FIDO2 Authentication Step 1 **********************************************//
    //******************************* Get challenge from the Server ********************************************//
    //**********************************************************************************************************//
    private fun fido2AuthInitiate() {

        val result = JSONObject()
        val mediaType = "application/json".toMediaTypeOrNull()
        result.put("username", usernameButton.text.toString())
        val requestBody = RequestBody.create(mediaType, result.toString())
        try {
            RPApiService.getApi().authInitiate(requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            val obj = JSONObject(response.body()?.string())
                            val c = obj?.getString("challenge")
                            val challenge = Base64.decode(c!!, BASE64_FLAG)
                            val allowCredentials = obj?.getJSONArray("allowCredentials")

                            fido2AndroidAuth(allowCredentials, challenge)

                            Log.d("response", response.message())
                        } else {
                            Log.d("response", response.errorBody().toString())
                            resultText.text = "Authentication Failed" + "\n" + response.toString()
                        }

                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("response", t.message)

                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //**********************************************************************************************************//
    //******************************* FIDO2 Authentication Step 2 **********************************************//
    //******************************* Invoke Android FIDO2 API  ************************************************//
    //**********************************************************************************************************//
    private fun fido2AndroidAuth(
        allowCredentials: JSONArray,
        challenge: ByteArray
    ) {
        try {
            val list = mutableListOf<PublicKeyCredentialDescriptor>()
            for (i in 0..(allowCredentials.length() - 1)) {
                val item = allowCredentials.getJSONObject(i)
                list.add(
                    PublicKeyCredentialDescriptor(
                        PublicKeyCredentialType.PUBLIC_KEY.toString(),
                        Base64.decode(item.getString("id"), BASE64_FLAG),
                        /* transports */ null
                    )
                )
            }

            val options = PublicKeyCredentialRequestOptions.Builder()
                .setRpId(RPID)
                .setAllowList(list)
                .setChallenge(challenge)
                .build()

            val fido2ApiClient = Fido.getFido2ApiClient(applicationContext)
            val fido2PendingIntentTask = fido2ApiClient.getSignIntent(options)
            fido2PendingIntentTask.addOnSuccessListener { fido2PendingIntent ->
                if (fido2PendingIntent.hasPendingIntent()) {
                    try {
                        Log.d(LOG_TAG, "launching Fido2 Pending Intent")
                        fido2PendingIntent.launchPendingIntent(this@MainActivity, REQUEST_CODE_SIGN)
                    } catch (e: IntentSender.SendIntentException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //**********************************************************************************************************//
    //******************************* FIDO2 Authentication Step 3 **********************************************//
    //**************** Send Signed Challenge (Assertion) to the Server for verification ************************//
    //**********************************************************************************************************//
    private fun fido2AuthComplete(fido2Response: ByteArray) {

        val assertionResponse = AuthenticatorAssertionResponse.deserializeFromBytes(fido2Response)
        val credId = Base64.encodeToString(assertionResponse.keyHandle, BASE64_FLAG)
        val signature = Base64.encodeToString(assertionResponse.signature, BASE64_FLAG)
        val authenticatorData =
            Base64.encodeToString(assertionResponse.authenticatorData, BASE64_FLAG)
        val clientDataJson = Base64.encodeToString(assertionResponse.clientDataJSON, BASE64_FLAG)


        val response = JSONObject()
        response.put("clientDataJSON", clientDataJson)
        response.put("signature", signature)
        response.put("userHandle", "")
        response.put("authenticatorData", authenticatorData)

        val jsonObject = JSONObject()
        jsonObject.put("type", "public-key")
        jsonObject.put("id", credId)
        jsonObject.put("rawId", credId)
        jsonObject.put("getClientExtensionResults", JSONObject())
        jsonObject.put("response", response)

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonObject.toString())

        try {
            RPApiService.getApi()
                .authComplete("username=${usernameButton.text.toString()}", requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            resultText.text = "Authentication Successful"
                            Log.d("response", response.message())
                        } else {
                            Log.d("response", response.errorBody().toString())
                            resultText.text = "Authentication Failed" + "\n" + response.toString()
                        }

                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("response", t.message)

                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}