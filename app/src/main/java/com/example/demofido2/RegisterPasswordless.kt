package com.example.demofido2

import android.content.Context
import android.content.Intent
import android.content.IntentSender
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
import java.util.concurrent.Executors
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.Exception

class RegisterPasswordless: ComponentActivity() {

    private lateinit var btnRegPasswordless: Button
    private lateinit var btnSignout: Button

    companion object {
        private const val LOG_TAG = "Fido2Demo"
        private const val REQUEST_CODE_REGISTER = 1
        private const val REQUEST_CODE_SIGN = 2
        private const val KEY_HANDLE_PREF = "key_handle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("accessToken")){
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }else{
            val accessToken = sharedPreferences.getString("accessToken", null);
            setContentView(R.layout.reg_passwordless)
            btnRegPasswordless = findViewById(R.id.btnRegPasswordless);
            btnSignout = findViewById(R.id.btnSignout);
            btnSignout.setOnClickListener{
                sharedPreferences.edit().remove("accessToken").apply();
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            btnRegPasswordless.setOnClickListener {
                if (accessToken != null) {
                    fido2RegisterInitiate(accessToken)
                };
            }
        }
    }

    //**********************************************************************************************************//
    //******************************* Android FIDO2 API Response ***********************************************//
    //**********************************************************************************************************//
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(LOG_TAG, "onActivityResult - requestCode: $requestCode, resultCode: $resultCode")

        when (resultCode) {
            RESULT_OK -> {
                data?.let {
                    if (it.hasExtra(Fido.FIDO2_KEY_ERROR_EXTRA)) {

                        val errorExtra = data.getByteArrayExtra(Fido.FIDO2_KEY_ERROR_EXTRA)
                        val authenticatorErrorResponse =
                            errorExtra?.let { it1 ->
                                AuthenticatorErrorResponse.deserializeFromBytes(
                                    it1
                                )
                            }
                        val errorName = authenticatorErrorResponse?.errorCode?.name
                        val errorMessage = authenticatorErrorResponse?.errorMessage

                        Log.e(LOG_TAG, "errorCode.name: $errorName")
                        Log.e(LOG_TAG, "errorMessage: $errorMessage")

//                        resultText.text =
//                            "An Error Occurred\n\nError Name:\n$errorName\n\nError Message:\n$errorMessage"
                        runOnUiThread {
                            Toast.makeText(applicationContext, "An Error Occurred\n\nError Name:\n$errorName\n\nError Message:\n$errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    } else if (it.hasExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)) {
                        val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
                        val accessToken = sharedPreferences.getString("accessToken", null);
                        val fido2Response = data.getByteArrayExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)
                        when (requestCode) {
                            REQUEST_CODE_REGISTER -> fido2Response?.let { it1 ->
                                if (accessToken != null) {
                                    fido2RegisterComplete(
                                        it1, accessToken
                                    )
                                }
                            }
                        }
                    }
                }
            }
            RESULT_CANCELED -> {
                val result = "Operation is cancelled"
                runOnUiThread {
                    Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                }
//                resultText.text = result
                Log.d(LOG_TAG, result)
            }
            else -> {
                val result = "Operation failed, with resultCode: $resultCode"
                runOnUiThread {
                    Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                }
//                resultText.text = result
                Log.e(LOG_TAG, result)
            }
        }
    }

    //**********************************************************************************************************//
    //******************************* FIDO2 Registration Step 1 ************************************************//
    //******************************* Get challenge from the Server ********************************************//
    //**********************************************************************************************************//
    private fun fido2RegisterInitiate(accessToken: String) {

        val mediaType = "application/json".toMediaTypeOrNull()


        //Optional
        val jsonObject = JSONObject()
        jsonObject.put("attestation", "none")
        jsonObject.put("username", "")
        val authenticatorSelection = JSONObject();
        authenticatorSelection.put("authenticatorAttachment","platform")
        authenticatorSelection.put("residentKey","required")
        authenticatorSelection.put("requireResidentKey", false)
        authenticatorSelection.put("userVerification", "preferred")
        jsonObject.put("authenticatorSelection", authenticatorSelection)

        val requestBody = jsonObject.toString().toRequestBody(mediaType)

        try {
            RPApiService.getApi(accessToken).registerInitiate(requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            try {
                                val intiateResponse = response.body()?.string()?.let { JSONObject(it) }
                                val c = intiateResponse?.getString("challenge")
                                val challenge = Base64.decode(c!!, BASE64_FLAG)
                                var rpname = intiateResponse?.getJSONObject("rp")!!.getString("name")
                                var username =
                                    intiateResponse?.getJSONObject("user")!!.getString("name")
                                var userId = intiateResponse?.getJSONObject("user")!!.getString("id")

                                var authenticatorAttachement = ""
                                if (intiateResponse.has("authenticatorSelection")) {
                                    if (intiateResponse?.getJSONObject("authenticatorSelection")!!
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
                                if (attestation != null) {
                                    Log.d(LOG_TAG, attestation)
                                }
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
                            }catch (e:Exception){
                                runOnUiThread {
                                    Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
                                }
                            }

                        } else {
                            runOnUiThread {
                                Toast.makeText(applicationContext, response.toString(), Toast.LENGTH_SHORT).show()
                            }
//                            resultText.text = response.toString()
                        }

                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        t.message?.let { Log.d(LOG_TAG, it) }
//                        resultText.text = t.message
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
//            val fido2PendingIntentTask = fido2ApiClient.getRegisterPendingIntent(options.build());
            val fido2PendingIntentTask = fido2ApiClient.getRegisterIntent(options.build())
            fido2PendingIntentTask.addOnSuccessListener { fido2PendingIntent ->
                if (fido2PendingIntent.hasPendingIntent()) {
                    try {
                        Log.d(LOG_TAG, "launching Fido2 Pending Intent")
                        fido2PendingIntent.launchPendingIntent(
                            this@RegisterPasswordless,
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
    private fun fido2RegisterComplete(fido2Response: ByteArray, accessToken: String) {
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
            RPApiService.getApi(accessToken)
//                .registerComplete("username=${usernameButton.text.toString()}", requestBody)
                .registerComplete("username=abc", requestBody)
                //.registerComplete( requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            runOnUiThread {
                                Toast.makeText(applicationContext,
                                    "Registration Successful", Toast.LENGTH_SHORT).show()
                            }
//                            resultText.text = "Registration Successful"
                            Log.d("response", response.message())
                        } else {
                            runOnUiThread {
                                Toast.makeText(applicationContext,
                                    "Registration Failed\n$response", Toast.LENGTH_SHORT).show()
                            }
//                            resultText.text = "Registration Failed" + "\n" + response.toString()
                            Log.d("response", response.errorBody().toString())
                        }

                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        t.message?.let { Log.d("response", it) }

                    }
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}