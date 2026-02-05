package com.omnistream.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnistream.data.local.DownloadEntity
import com.omnistream.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository.allDownloads
        .debounce(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.pauseDownload(downloadId)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.resumeDownload(downloadId)
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(download)
        }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            downloadRepository.enqueueDownload(
                download.copy(status = "pending", progress = 0f)
            )
        }
    }
}
