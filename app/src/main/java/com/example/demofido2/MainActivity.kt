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
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.demofido2.ui.theme.DemoFido2Theme
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient;
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.Exception
import java.util.concurrent.Executor
import java.util.concurrent.Executors


var RP_SERVER_URL = "https://testesignwebsiteadminrd.misa.com.vn";

var RPID = "testesignwebsiteadminrd.misa.com.vn";
const val BASE64_FLAG = Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE

class MainActivity : ComponentActivity() {

    private lateinit var loginButton: Button
    private lateinit var passwordlessLoginButton: Button
    private lateinit var usernameText: EditText
    private lateinit var paswordText: EditText
    private lateinit var _id: String

    companion object {
        private const val LOG_TAG = "Fido2Demo"
        private const val REQUEST_CODE_REGISTER = 1
        private const val REQUEST_CODE_SIGN = 2
        private const val KEY_HANDLE_PREF = "key_handle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        if (sharedPreferences.contains("accessToken")){
            val intent = Intent(this, RegisterPasswordless::class.java)
            startActivity(intent)
        }else{
            setContentView(R.layout.main_activity)
            loginButton = findViewById(R.id.loginButton)
            usernameText = findViewById(R.id.usernameText)
            paswordText = findViewById(R.id.passwordText)
            passwordlessLoginButton = findViewById(R.id.passwordlessLoginButton)
            passwordlessLoginButton.setOnClickListener {
                fido2AuthInitiate();
            }
            loginButton.setOnClickListener{
                val username = usernameText.text.toString()
                val password = paswordText.text.toString()
                Executors.newSingleThreadExecutor().execute {
                    var loginRes = login(username,password);
                    if (loginRes==null||!loginRes.Success){
                        runOnUiThread{
                            Toast.makeText(applicationContext, "Login failed!", Toast.LENGTH_SHORT).show()
                        }
                    }else{
                        try{
                            val editor: SharedPreferences.Editor = sharedPreferences.edit()
                            editor.putString("accessToken", loginRes.Data.AccessToken);
                            editor.apply()
                            val intent = Intent(this, RegisterPasswordless::class.java)
                            startActivity(intent)
                        }catch (e: Exception){
                            println(e)
                        }

                    }
                }

            }
        }
    }

    //**********************************************************************************************************//
    //******************************* Android FIDO2 API Response ***********************************************//
    //**********************************************************************************************************//
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(MainActivity.LOG_TAG, "onActivityResult - requestCode: $requestCode, resultCode: $resultCode")

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

                        Log.e(MainActivity.LOG_TAG, "errorCode.name: $errorName")
                        Log.e(MainActivity.LOG_TAG, "errorMessage: $errorMessage")

//                        resultText.text =
//                            "An Error Occurred\n\nError Name:\n$errorName\n\nError Message:\n$errorMessage"
                        runOnUiThread {
                            Toast.makeText(applicationContext, "An Error Occurred\n\nError Name:\n$errorName\n\nError Message:\n$errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    } else if (it.hasExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)) {
                        val fido2Response = data.getByteArrayExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)
                        when (requestCode) {
                            MainActivity.REQUEST_CODE_SIGN -> fido2Response?.let { it1 -> fido2AuthComplete(it1) }
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
                Log.d(MainActivity.LOG_TAG, result)
            }
            else -> {
                val result = "Operation failed, with resultCode: $resultCode"
                runOnUiThread {
                    Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                }
//                resultText.text = result
                Log.e(MainActivity.LOG_TAG, result)
            }
        }
    }

    private fun login(username: String, password: String) : MisaIdResponse?{
        try{
            val url = "https://testesignwebsiteadminrd.misa.com.vn/api/account/login"
            val json = """
        {
            "clientId": "fd79d62e-8a6f-441f-892e-4788d95b560a",
            "scopes": "openid profile",
            "userName": "$username",
            "password": "$password"
        }
    """.trimIndent()

            val mediaType = "application/json".toMediaType()
            val requestBody = json.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Cookie", ".AspNetCore.Identity.Application=CfDJ8AXIIsILFP5LntKBqVm-n2nHwLZumgbnsexS0WS2pa2u5RhGBJfdWDaq6IoOi969P7s3Yf_7yWQ4gYEOCPcGRVPaE0P1wfCNir5s9k_CDAVMdngR1v-inAiTnytLnqD8lr7o6JBQ9-qEK0q5-4YHH9DQbSXot9vM2H-cW2UwKJOoynVPvutzXd0eRQ-mq44t3_rWhp7kvgvh5dMJmZJkA3IklNFbiNg1KHY_JqUuy0aOWSRhFsE2CKvvlnJLFyWvGrZl-V2GcGXShkTwFFCcnPK5POvkiPhBhZo8p7o3UXDphjE97Bh58gfMiff58Xfho_Sxx7tAoPHEmG3liuoe_EY; Identity.TwoFactorUserId=CfDJ8AXIIsILFP5LntKBqVm-n2kmyOwuu9tkbbcJzkjIhRfFzeGt51uaUXjlfRPeQQfxZjhAd5zrGpUK86eSeJvuzJl2HsXy3iIeEnL0xOEGoa6wwMjujcgvQ_YIgiKSFPfQGgN2XBnKhsrH5J-VAEolXvB07QDkUEAnHJySVgUEREEEO67OCgIHjTI7NbmyDHKWPCBgIaR2bS8YxA6flwJl5K2abu62WJZDoYnt1gg4WwTNdecrpv3j2n5_mDcphWFD1xok5gUbIcTdyAFVldM3J5q9MqntJXmyoG4kSVyng_mxqlMAlU6aCZYQr1nydJDVyFRNI-Tt4BT6Ub6HIGO2E9bMS2HciWWra3zOec8od_tuGAESitFsvMviswadOQv2uA; TS01324ff8=010fb974040b59b52812a14f6e59ca4900cf806073e63520c5ae342f40aa9d3fe03349d732d4351f721ac35a8d12b9e76c6629efac9fbf0b8ea134d3a466e44091f0ee05f75ca343f969071b1d0cd2e0f90151804aac36f337f30b4bd49c70d2c09ec536d9b31aea6b97dd32788a2752222f1bae49e13b5ee779dcaf77bc3701ecc70ef698; idsrv.session=d415733da026fdc2856bf2827fda3f9d")
                .post(requestBody)
                .build()

            val client = OkHttpClient()
            val response = client.newCall(request).execute()

            val responseBody = response.body?.string()
            println(responseBody)
            val gson = Gson()
            return gson.fromJson(responseBody, MisaIdResponse::class.java)
        }catch(e: Exception){
            println(e)
            return null;
        }
    }
    //**********************************************************************************************************//
    //******************************* FIDO2 Authentication Step 1 **********************************************//
    //******************************* Get challenge from the Server ********************************************//
    //**********************************************************************************************************//
    private fun fido2AuthInitiate() {

        val result = JSONObject()
        val mediaType = "application/json".toMediaTypeOrNull()
//        result.put("username", usernameButton.text.toString())
        val requestBody = RequestBody.create(mediaType, result.toString())
        try {
            RPApiService.getApi().authInitiate(requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            val res = JSONObject(response.body()?.string());
                            _id = res.getString("id");
                            val obj = res.getJSONObject("assertionOptions")
                            val c = obj.getString("challenge")
                            val challenge = Base64.decode(c, BASE64_FLAG)
                            val allowCredentials = obj.getJSONArray("allowCredentials")

                            if (allowCredentials != null) {
                                fido2AndroidAuth(allowCredentials, challenge)
                            }

                            Log.d("response", response.message())
                        } else {
                            Log.d("response", response.errorBody().toString())
                            runOnUiThread {
                                Toast.makeText(applicationContext,
                                    "Authentication Failed\n$response", Toast.LENGTH_SHORT).show()
                            }
//                            resultText.text = "Authentication Failed\n$response"
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
//            val keys = loadKeyHandle();
//            for (key in keys){
//                list.add(
//                    PublicKeyCredentialDescriptor(
//                        PublicKeyCredentialType.PUBLIC_KEY.toString(),
//                        key,
//                        null
//                    )
//                )
//            }

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
                        Log.d(MainActivity.LOG_TAG, "launching Fido2 Pending Intent")
                        fido2PendingIntent.launchPendingIntent(this@MainActivity,
                            MainActivity.REQUEST_CODE_SIGN
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
    //******************************* FIDO2 Authentication Step 3 **********************************************//
    //**************** Send Signed Challenge (Assertion) to the Server for verification ************************//
    //**********************************************************************************************************//
    private fun fido2AuthComplete(fido2Response: ByteArray) {

        val assertionResponse = AuthenticatorAssertionResponse.deserializeFromBytes(fido2Response)
        val credId = Helper.coerceToBase64Url(assertionResponse.keyHandle, BASE64_FLAG)

//        val credId = Helper.coerceToArrayBuffer(assertionResponse.keyHandle, "keyHandle" , BASE64_FLAG)
        val signature = Helper.coerceToBase64Url(assertionResponse.signature, BASE64_FLAG)
        val authenticatorData =
            Helper.coerceToBase64Url(assertionResponse.authenticatorData, BASE64_FLAG)
        val clientDataJson = Helper.coerceToBase64Url(assertionResponse.clientDataJSON, BASE64_FLAG)


        val response = JSONObject()
        response.put("clientDataJSON", clientDataJson)
        response.put("signature", signature)
//        response.put("userHandle", "")
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
//                .authComplete("username=${usernameButton.text.toString()}", requestBody)
                .authComplete(_id,"username=abc", requestBody)
                .enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            runOnUiThread {
                                Toast.makeText(applicationContext,
                                    "Authentication Successful", Toast.LENGTH_SHORT).show()
                                try{
                                    val res = response.body()?.string()?.let { JSONObject(it) };
                                    val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
                                    val editor: SharedPreferences.Editor = sharedPreferences.edit()
                                    if (res != null) {
                                        editor.putString("accessToken", res.getString("accessToken"))
                                    };
                                    editor.apply()
                                    val intent = Intent(applicationContext, RegisterPasswordless::class.java)
                                    startActivity(intent)
                                }catch (e: Exception){
                                    println(e)
                                }
                            }
//                            resultText.text = "Authentication Successful"
                            Log.d("response", response.message())

                        } else {
                            Log.d("response", response.errorBody().toString())
                            runOnUiThread {
                                Toast.makeText(applicationContext,
                                    "Authentication Failed\n$response", Toast.LENGTH_SHORT).show()
                            }
//                            resultText.text = "Authentication Failed\n$response"
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

    private fun loadKeyHandle(): HashSet<ByteArray> {
        val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val keys = sharedPreferences.getStringSet(MainActivity.KEY_HANDLE_PREF, HashSet<String>());
//        val keyHandleBase64 = sharedPreferences
//            .getString(MainActivity.KEY_HANDLE_PREF, null);
        val res = HashSet<ByteArray>();
        if (keys != null) {
            for (item in keys){
                res.add(Base64.decode(item, BASE64_FLAG));
            }
        }
        return res;
    }
}


//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            DemoFido2Theme {
//                // A surface container using the 'background' color from the theme
//                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
//                    Greeting("Android")
//                }
//            }
//        }
//    }
//}

