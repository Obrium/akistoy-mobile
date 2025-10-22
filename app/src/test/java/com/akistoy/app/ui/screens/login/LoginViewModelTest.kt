package com.akistoy.app.ui.screens.login

import com.akistoy.app.domain.model.User
import com.akistoy.app.domain.repo.AuthRepository
import com.akistoy.app.domain.usecase.LoginUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeAuthRepository(private val result: Result<User>) : AuthRepository {
    override suspend fun login(email: String, deviceId: String): Result<User> = result
    override suspend fun getLoggedInUser(): User? = result.getOrNull()
    override suspend fun logout() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @Test
    fun login_success_updates_state() = runTest {
        val user = User(id = "1", email = "test", token = "token", deviceId = "device")
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository(Result.success(user))))

        viewModel.onEmailChange("test")
        viewModel.login()

        val state = viewModel.state.value
        assertTrue(state.success)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun login_failure_sets_error() = runTest {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository(Result.failure(Exception("Boom")))))

        viewModel.onEmailChange("test")
        viewModel.login()

        val state = viewModel.state.value
        assertEquals("Boom", state.error)
        assertEquals(false, state.success)
    }
}
