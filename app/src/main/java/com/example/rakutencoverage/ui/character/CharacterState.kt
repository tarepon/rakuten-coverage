package com.example.rakutencoverage.ui.character

import com.example.rakutencoverage.data.SignalLevel

data class CharacterState(
    val level: SignalLevel?,
    val emoji: String,
    val message: String
)

private val messages = mapOf(
    SignalLevel.PLATINUM_5G to listOf(
        "プラチナ5G！！楽天最高〜！！！",
        "これがプラチナ×5Gのチカラ…！！",
        "全力全開！！！テンション振り切れた！！"
    ),
    SignalLevel.FIVE_G to listOf(
        "5Gだ！はやい！！",
        "5G来たー！すごいすごい！！",
        "うわっ5G！気持ちいい！！"
    ),
    SignalLevel.PLATINUM to listOf(
        "プラチナバンド来たー！つながりやすい！",
        "700MHz帯、滲みる…！",
        "プラチナ検知！ここ安心して使えそう！"
    ),
    SignalLevel.LTE to listOf(
        "計測中…よし、つながってる！",
        "LTE順調！快適快適〜",
        "ふつうにつながってるよ！"
    ),
    SignalLevel.WEAK to listOf(
        "パートナー回線につながってる…",
        "ここ弱いな…記録しとく",
        "…（無言）"
    ),
    SignalLevel.NO_SIGNAL to listOf(
        "圏外…ここは電波届かないか",
        "うーん、圏外。記録はしとく",
        "電波なし。また別の場所で試そう"
    ),
    SignalLevel.AIRPLANE_MODE to listOf(
        "機内モードだよ！スタンプは無効ね",
        "✈️ 機内モード中。計測データは保存するけどスタンプはカウントしないよ",
        "飛行機？旅行？機内モードは圏外扱いにはならないよ"
    ),
    SignalLevel.NO_SIM to listOf(
        "SIMが入ってないよ！計測できないよ",
        "SIMカードがないので正確な計測ができません",
        "SIMなし。スタンプも無効だよ"
    )
)

fun SignalLevel.toCharacterState(): CharacterState {
    val msg = messages[this]?.random() ?: "…"
    val emoji = when (this) {
        SignalLevel.PLATINUM_5G -> "🤩"
        SignalLevel.FIVE_G      -> "😆"
        SignalLevel.PLATINUM    -> "😊"
        SignalLevel.LTE         -> "🙂"
        SignalLevel.WEAK        -> "😔"
        SignalLevel.NO_SIGNAL   -> "😶"
        SignalLevel.AIRPLANE_MODE -> "✈️"
        SignalLevel.NO_SIM        -> "📵"
    }
    return CharacterState(this, emoji, msg)
}

fun idleCharacterState() = CharacterState(null, "😶", "待機中…計測ボタンを押してね")
