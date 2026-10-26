package com.example.upaos.ui.profile

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.profile.ProfileRepository
import com.example.upaos.data.profile.ProfileResult
import com.example.upaos.data.profile.StudentProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileState(val profile: StudentProfile? = null, val loading: Boolean = false, val message: String? = null)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val tokens = TokenManager(app)
    private val user = tokens.getSavedUser()
    private val session = tokens.getSessionId()
    private val repository = if (user != null && session != null) ProfileRepository(app, user, session) else null
    private val mutable = MutableStateFlow(ProfileState())
    val state = mutable.asStateFlow()
    private var loaded = false
    private var job: Job? = null
    init { refresh() }
    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val repo = repository ?: return@launch
            if (!repo.isCurrent()) { mutable.value = ProfileState(); return@launch }
            mutable.value = mutable.value.copy(loading = true, message = null)
            try {
                if (!loaded) {
                    val cached = repo.cached()
                    if (!repo.isCurrent()) { mutable.value = ProfileState(); return@launch }
                    mutable.value = mutable.value.copy(profile = cached)
                    loaded = true
                }
                val result = repo.refresh()
                if (!repo.isCurrent()) { mutable.value = ProfileState(); return@launch }
                mutable.value = when (result) {
                    is ProfileResult.Updated -> ProfileState(profile = result.profile)
                    ProfileResult.AuthenticationRequired -> mutable.value.copy(loading = false,
                        message = "No se pudo iniciar la sesión de Experience. Vuelve a iniciar sesión en UPAO S y actualiza el perfil.")
                    ProfileResult.Unavailable -> mutable.value.copy(loading = false,
                        message = "No se pudo actualizar el perfil. Tus datos guardados se conservan.")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, message = "El perfil no está disponible en este momento.")
            }
        }
    }
}

@Composable
fun rememberProfileViewModel(owner: ViewModelStoreOwner): ProfileViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(viewModelStoreOwner = owner, key = "student_profile", factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(app) as T
        }
    })
}
