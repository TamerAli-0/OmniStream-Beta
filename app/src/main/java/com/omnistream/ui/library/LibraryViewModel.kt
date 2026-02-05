package com.omnistream.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.FavoriteDao
import com.omnistream.data.local.FavoriteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val favorites: List<FavoriteEntity> = emptyList()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val favoriteDao: FavoriteDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            favoriteDao.getAllFavorites().collect { favorites ->
                _uiState.value = LibraryUiState(
                    isLoading = false,
                    favorites = favorites
                )
            }
        }
    }
}
