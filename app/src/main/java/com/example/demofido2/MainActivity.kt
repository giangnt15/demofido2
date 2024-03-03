package com.example.demofido2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient;
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.Exception
import java.util.concurrent.Executor
import java.util.concurrent.Executors

var RP_SERVER_URL = "https://webauthndemo.singularkey.com";

class MainActivity : ComponentActivity() {

    private lateinit var loginButton: Button
    private lateinit var usernameText: EditText
    private lateinit var paswordText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences: SharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        setContentView(R.layout.main_activity)
        loginButton = findViewById(R.id.loginButton)
        usernameText = findViewById(R.id.usernameText)
        paswordText = findViewById(R.id.passwordText)
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

