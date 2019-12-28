package com.example.googlemlkit

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.*
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

class NetworkClient {

    private var BASE_URL: String = "http://10.0.2.2:4000"
    private lateinit var retrofit: Retrofit

    public fun getRetrofitClient(): Retrofit {
        var okHttpClient: OkHttpClient = OkHttpClient().newBuilder().build()
        return Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
    }
}

interface UploadAPIs {
    @Multipart
    @POST("/image/reader")
    fun uploadImage(@Part file: MultipartBody.Part, @Part("file") requestBody: RequestBody) : Call<ImageResponse>

    @GET("/status")
    fun checkStatus() : Call<StatusResponse>

}

class ImageResponse {
    @SerializedName("message")
    var message: String = ""
}

class StatusResponse {
    @SerializedName("status")
    var status: String = ""
}