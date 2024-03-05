package com.example.demofido2

import okhttp3.Interceptor
import okhttp3.Interceptor.*
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit


interface RPApi {
    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("/api/account/fido2/mobile/attestation/options")
    fun registerInitiate(@Body postBody: RequestBody): Call<ResponseBody>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("/api/account/fido2/attestation/result")
    fun registerComplete(
        @Query("id") id: String,
        @Header("Cookie") cookie: String,
        @Body body: RequestBody
    ): Call<ResponseBody>
    //fun registerComplete(@Body body: RequestBody): Call<ResponseBody>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("/api/account/fido2/mobile/assertion/options")
    fun authInitiate(@Body postBody: RequestBody): Call<ResponseBody>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("/api/account/fido2/assertion/result")
    fun authComplete(
        @Query("id") id: String,
        @Header("Cookie") cookie: String,
        @Body postBody: RequestBody
    ): Call<ResponseBody>

}

class RPApiService {

    companion object {
        var cookieManager: CookieManager? = null

        fun getApi(): RPApi {

            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            if (cookieManager == null) {
                cookieManager = CookieManager()
                cookieManager!!.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            }

            val okHttpClientBuilder = OkHttpClient().newBuilder() //create OKHTTPClient
            okHttpClientBuilder.cookieJar(JavaNetCookieJar(cookieManager!!))

            val okHttpClient = okHttpClientBuilder
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(interceptor)
                .build()
            val retrofit =
                Retrofit.Builder().client(okHttpClient)
                    .baseUrl(RP_SERVER_URL)
                    .build();

            return retrofit.create(RPApi::class.java)
        }

        fun getApi(accessToken: String) : RPApi {

            val interceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            if (cookieManager == null) {
                cookieManager = CookieManager()
                cookieManager!!.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            }

            val okHttpClientBuilder = OkHttpClient().newBuilder() //create OKHTTPClient
            okHttpClientBuilder.cookieJar(JavaNetCookieJar(cookieManager!!))

            val okHttpClient = okHttpClientBuilder
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(interceptor)
                .addInterceptor(Interceptor { chain ->
                    val request: Request =
                        chain.request().newBuilder().addHeader("Authorization", "Bearer $accessToken").build()
                    chain.proceed(request)
                })
                .build()
            val retrofit =
                Retrofit.Builder().client(okHttpClient)
                    .baseUrl(RP_SERVER_URL)
                    .build();

            return retrofit.create(RPApi::class.java)
        }
    }

}