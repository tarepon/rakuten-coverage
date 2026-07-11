package com.example.rakutencoverage.ui.checkin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rakutencoverage.data.AppDatabase
import com.example.rakutencoverage.data.CheckInRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * チェックイン記録一覧(CheckInScreen)の状態管理。
 * アクティブなチェックイン(計測ループへのタグ付け)自体は Activity スコープの
 * MapViewModel._checkIn が引き続き保持する — このViewModelは記録の閲覧のみを担当する。
 */
class CheckInViewModel(app: Application) : AndroidViewModel(app) {

    private val checkInDao = AppDatabase.getInstance(app).checkInDao()

    val records: StateFlow<List<CheckInRecord>> = checkInDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
