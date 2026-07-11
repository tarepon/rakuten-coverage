package com.example.rakutencoverage.ui.map

import com.example.rakutencoverage.data.GamePhase
import com.example.rakutencoverage.data.Spot

/**
 * アリーナチェックイン中の選択内容。GPS計測時のタグ付け(MapViewModel._checkIn)に使う。
 * チェックインの入力UI自体は ui/checkin/CheckInInputScreen.kt に移った(旧 ArenaModeDialog.CheckInDialog)。
 * この data class は計測ループのタグ付けで引き続き使われるため、ここに残している。
 */
data class ArenaModeInput(
    val spot: Spot,
    val seatLabel: String = "",
    val gamePhase: GamePhase = GamePhase.PRE_GAME
)
