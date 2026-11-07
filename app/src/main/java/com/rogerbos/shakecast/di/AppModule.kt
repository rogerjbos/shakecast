package com.rogerbos.shakecast.di

import com.rogerbos.shakecast.BuildConfig
import com.rogerbos.shakecast.data.remote.itunes.ItunesSearchApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val USER_AGENT = "Shakecast/0.1 (Android; +https://github.com/rogerjbos/shakecast)"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        // Sync upserts rows in bulk; PostgREST requires every row to carry the same keys (PGRST102).
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder().header("User-Agent", USER_AGENT)
            // Private member feeds often carry credentials in the address; OkHttp parses but never sends them.
            if (request.url.username.isNotEmpty() && request.header("Authorization") == null) {
                builder.header("Authorization", Credentials.basic(request.url.username, request.url.password))
            }
            chain.proceed(builder.build())
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideItunesApi(client: OkHttpClient, json: Json): ItunesSearchApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ItunesSearchApi::class.java)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
