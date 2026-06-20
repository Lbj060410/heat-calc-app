package com.example.connects.core.di

import com.example.connects.data.repository.AiRepositoryImpl
import com.example.connects.data.repository.FoodRepositoryImpl
import com.example.connects.data.repository.UserRepositoryImpl
import com.example.connects.domain.repository.AiRepository
import com.example.connects.domain.repository.FoodRepository
import com.example.connects.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Singleton
    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Singleton
    @Binds
    abstract fun bindFoodRepository(impl: FoodRepositoryImpl): FoodRepository

    @Singleton
    @Binds
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository
}
