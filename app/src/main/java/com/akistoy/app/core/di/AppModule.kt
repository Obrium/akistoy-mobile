package com.akistoy.app.core.di

import com.akistoy.app.BuildConfig
import com.akistoy.app.core.util.AppDispatchers
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akistoy.app.data.beacon.BeaconScanner
import com.akistoy.app.data.beacon.RealBeaconScanner
import com.akistoy.app.data.beacon.SimulatedBeaconScanner
import com.akistoy.app.data.local.AkistoyDatabase
import com.akistoy.app.data.local.dao.TrustedBeaconDao
import dagger.hilt.android.qualifiers.ApplicationContext
import com.akistoy.app.data.remote.api.AkistoyApi
import com.akistoy.app.data.remote.interceptor.AuthInterceptor
import com.akistoy.app.data.remote.interceptor.ErrorHandlingInterceptor
import com.akistoy.app.data.remote.interceptor.RateLimitInterceptor
import com.akistoy.app.data.repository.AuthRepositoryImpl
import com.akistoy.app.data.repository.BeaconRepositoryImpl
import com.akistoy.app.data.repository.ConfigRepositoryImpl
import com.akistoy.app.data.repository.MarkRepositoryImpl
import com.akistoy.app.data.repository.UserPreferencesDataSource
import com.akistoy.app.domain.repo.AuthRepository
import com.akistoy.app.domain.repo.BeaconRepository
import com.akistoy.app.domain.repo.ConfigRepository
import com.akistoy.app.domain.repo.MarkRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        errorHandlingInterceptor: ErrorHandlingInterceptor,
        rateLimitInterceptor: RateLimitInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        // ESTABILIDAD: Orden de interceptors es importante
        // 1. Rate limiting (antes de todo)
        .addInterceptor(rateLimitInterceptor)
        // 2. Logging (para debug)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        })
        // 3. Auth (agregar token)
        .addInterceptor(authInterceptor)
        // 4. Error handling (manejar responses)
        .addInterceptor(errorHandlingInterceptor)
        // ESTABILIDAD: Agregar timeouts para prevenir requests colgados
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        // ESTABILIDAD: Retry en fallas de conexión
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideApi(json: Json, client: OkHttpClient): AkistoyApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AkistoyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(preferences: UserPreferencesDataSource): AuthInterceptor =
        AuthInterceptor(preferences)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindBeaconRepository(impl: BeaconRepositoryImpl): BeaconRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindMarkRepository(impl: MarkRepositoryImpl): MarkRepository

    @Binds
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository
}

@Module
@InstallIn(SingletonComponent::class)
object BeaconModule {
    @Provides
    @Singleton
    fun provideBeaconScanner(
        @ApplicationContext context: Context,
        realScanner: RealBeaconScanner,
        simulatedScanner: SimulatedBeaconScanner
    ): BeaconScanner {
        // Usar scanner simulado en modo debug para pruebas
        // Cambiar a false para usar hardware BLE real
        val useSimulation = BuildConfig.DEBUG && false  // false = beacons reales
        return if (useSimulation) simulatedScanner else realScanner
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AkistoyDatabase {
        return Room.databaseBuilder(
            context,
            AkistoyDatabase::class.java,
            "akistoy_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideTrustedBeaconDao(database: AkistoyDatabase): TrustedBeaconDao {
        return database.trustedBeaconDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers(
        io = kotlinx.coroutines.Dispatchers.IO,
        default = kotlinx.coroutines.Dispatchers.Default,
        main = kotlinx.coroutines.Dispatchers.Main
    )
}
