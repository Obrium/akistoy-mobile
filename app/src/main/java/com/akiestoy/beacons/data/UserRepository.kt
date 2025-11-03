package com.akiestoy.beacons.data

import com.akiestoy.beacons.model.user.User
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar datos de usuario
 */
class UserRepository(private val userDao: UserDao) {

    val currentUser: Flow<User?> = userDao.getCurrentUser()

    suspend fun getCurrentUserOnce(): User? {
        return userDao.getCurrentUserOnce()
    }

    suspend fun saveUser(user: User) {
        userDao.insertUser(user)
    }

    suspend fun clearUser() {
        userDao.deleteAll()
    }
}
