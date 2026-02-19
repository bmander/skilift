package com.skilift.app.di

import com.skilift.app.data.remote.OtpGraphQlClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Provides
    @Named("otpBaseUrl")
    fun provideOtpBaseUrl(): String = "http://10.0.2.2:8080"

    @Provides
    @Singleton
    fun provideOtpGraphQlClient(
        httpClient: HttpClient,
        @Named("otpBaseUrl") baseUrl: String
    ): OtpGraphQlClient = OtpGraphQlClient(httpClient, baseUrl)
}
