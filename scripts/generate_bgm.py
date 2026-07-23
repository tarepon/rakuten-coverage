#!/usr/bin/env python3
"""チップチューン風BGMを合成して app/src/main/res/raw/ にOGGで出力する。

外部ライブラリ不要(標準ライブラリのみ)。OGG変換にffmpegを使用。
生成曲はすべて本アプリのオリジナル(既存楽曲の引用なし)。

使い方:
    python3 scripts/generate_bgm.py

出力:
    app/src/main/res/raw/bgm_opening.ogg  オープニング(物語スクロール): 静かで壮大な短調
    app/src/main/res/raw/bgm_title.ogg    タイトル: 勇壮なファンファーレ風ループ
    app/src/main/res/raw/bgm_map.ogg      マップ/通常時: 軽快な冒険ループ
    app/src/main/res/raw/bgm_battle.ogg   捕獲ミニゲーム: 疾走感のある戦闘ループ
"""

import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

SR = 44100


def midi_freq(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


# ──────────────────────────────────────────────
# シンセ: 波形・エンベロープ
# ──────────────────────────────────────────────

def render_note(buf, start_sec, dur_sec, midi, wave_kind="square",
                vol=0.2, duty=0.5, attack=0.004, release=0.05, decay_to=1.0):
    """1音をbuf(floatリスト)に加算する。decay_to<1.0で音量が減衰していく(ベル風)"""
    if midi is None:
        return
    freq = midi_freq(midi)
    n0 = int(start_sec * SR)
    n = int(dur_sec * SR)
    atk = max(1, int(attack * SR))
    rel = max(1, int(release * SR))
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / SR
        phase = (freq * t) % 1.0
        if wave_kind == "square":
            s = 1.0 if phase < duty else -1.0
        elif wave_kind == "triangle":
            s = 4.0 * abs(phase - 0.5) - 1.0
        elif wave_kind == "saw":
            s = 2.0 * phase - 1.0
        else:  # sine
            s = math.sin(2 * math.pi * phase)
        # エンベロープ
        env = 1.0
        if i < atk:
            env = i / atk
        if n - i < rel:
            env = min(env, (n - i) / rel)
        if decay_to < 1.0:
            env *= 1.0 + (decay_to - 1.0) * (i / n)
        buf[idx] += s * vol * env


def render_kick(buf, start_sec, vol=0.5):
    """キック: 周波数が落ちるサイン波"""
    n0 = int(start_sec * SR)
    n = int(0.13 * SR)
    phase = 0.0
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / n
        freq = 130.0 * (1 - t) + 42.0 * t
        phase += freq / SR
        env = math.exp(-5.0 * t)
        buf[idx] += math.sin(2 * math.pi * phase) * vol * env


def render_snare(buf, start_sec, rng, vol=0.3):
    """スネア: ノイズ+短いトーン"""
    n0 = int(start_sec * SR)
    n = int(0.14 * SR)
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / n
        env = math.exp(-7.0 * t)
        tone = math.sin(2 * math.pi * 185.0 * i / SR) * 0.4
        buf[idx] += (rng.uniform(-1, 1) * 0.8 + tone) * vol * env


def render_hat(buf, start_sec, rng, vol=0.08):
    """ハイハット: 短い高域ノイズ(差分でローカット風)"""
    n0 = int(start_sec * SR)
    n = int(0.04 * SR)
    prev = 0.0
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        cur = rng.uniform(-1, 1)
        env = math.exp(-10.0 * i / n)
        buf[idx] += (cur - prev) * vol * env
        prev = cur


# ──────────────────────────────────────────────
# シーケンサ
# ──────────────────────────────────────────────

class Song:
    def __init__(self, bpm, beats, seed=1):
        self.bpm = bpm
        self.spb = 60.0 / bpm  # 秒/拍
        self.length_sec = beats * self.spb
        self.buf = [0.0] * int(self.length_sec * SR)
        self.rng = random.Random(seed)

    def note(self, beat, dur_beats, midi, **kw):
        render_note(self.buf, beat * self.spb, dur_beats * self.spb, midi, **kw)

    def kick(self, beat, vol=0.5):
        render_kick(self.buf, beat * self.spb, vol)

    def snare(self, beat, vol=0.3):
        render_snare(self.buf, beat * self.spb, self.rng, vol)

    def hat(self, beat, vol=0.08):
        render_hat(self.buf, beat * self.spb, self.rng, vol)

    def write_wav(self, path):
        peak = max(1e-9, max(abs(v) for v in self.buf))
        gain = 0.85 / peak if peak > 0.85 else 1.0
        frames = bytearray()
        for v in self.buf:
            s = int(max(-1.0, min(1.0, v * gain)) * 32767)
            frames += struct.pack("<h", s)
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(bytes(frames))


# ──────────────────────────────────────────────
# 各曲
# ──────────────────────────────────────────────

# コード定義(MIDIルート、構成音は相対)
MAJ = (0, 4, 7)
MIN = (0, 3, 7)


def build_title():
    """タイトル: ハ長調 112BPM 8小節。勇壮なファンファーレ風"""
    s = Song(112, 32, seed=11)
    chords = [(48, MAJ), (43, MAJ), (45, MIN), (41, MAJ),
              (48, MAJ), (41, MAJ), (43, MAJ), (48, MAJ)]  # C G Am F / C F G C
    melody = [
        (67, 72, 76, 74), (71, 74, 79, 74), (69, 72, 76, 72), (65, 69, 74, 72),
        (67, 72, 76, 79), (69, 72, 77, 76), (71, 74, 79, 81), (84, 79, 76, 72),
    ]
    for bar in range(8):
        base = bar * 4
        root, kind = chords[bar]
        # ベース: 8分でルートと5度
        for i in range(8):
            m = root if i % 4 != 3 else root + 7
            s.note(base + i * 0.5, 0.45, m, wave_kind="square", duty=0.5, vol=0.20)
        # アルペジオ(三角波): 8分で分散和音
        for i in range(8):
            m = root + 12 + kind[i % 3]
            s.note(base + i * 0.5, 0.4, m, wave_kind="triangle", vol=0.10)
        # メロディ(矩形波リード): 4分
        for i, m in enumerate(melody[bar]):
            s.note(base + i, 0.92, m, wave_kind="square", duty=0.25, vol=0.20)
        # ドラム
        for b in (0, 2):
            s.kick(base + b)
        for b in (1, 3):
            s.snare(base + b)
        for i in range(8):
            s.hat(base + i * 0.5)
    return s


def build_opening():
    """オープニング: イ短調 76BPM 8小節。静かで壮大、物語スクロール用"""
    s = Song(76, 32, seed=22)
    chords = [(45, MIN), (41, MAJ), (48, MAJ), (43, MAJ),
              (45, MIN), (41, MAJ), (40, MAJ), (45, MIN)]  # Am F C G / Am F E Am
    bells = [
        [(0, 2, 69), (2, 2, 72)], [(0, 3, 76), (3, 1, 74)],
        [(0, 2, 72), (2, 2, 71)], [(0, 4, 67)],
        [(0, 2, 69), (2, 2, 72)], [(0, 2, 76), (2, 2, 81)],
        [(0, 4, 80)], [(0, 4, 81)],
    ]
    for bar in range(8):
        base = bar * 4
        root, kind = chords[bar]
        # パッド(三角波3声、ゆっくり立ち上がる)
        for iv in kind:
            s.note(base, 3.9, root + 12 + iv, wave_kind="triangle",
                   vol=0.09, attack=0.35, release=0.4)
        # 低音ルート
        s.note(base, 3.9, root, wave_kind="triangle", vol=0.14, attack=0.2, release=0.4)
        # ベル風メロディ(細い矩形波、減衰)
        for off, dur, m in bells[bar]:
            s.note(base + off, dur * 0.95, m, wave_kind="square", duty=0.125,
                   vol=0.11, decay_to=0.2, release=0.3)
        # 小節頭に柔らかいキック
        s.kick(base, vol=0.25)
    return s


def build_map():
    """マップ/通常時: ヘ長調 126BPM 8小節。軽快な冒険ループ"""
    s = Song(126, 32, seed=33)
    chords = [(41, MAJ), (46, MAJ), (41, MAJ), (48, MAJ),
              (41, MAJ), (46, MAJ), (48, MAJ), (41, MAJ)]  # F Bb F C / F Bb C F
    melody = [
        (65, 69, 72, 69), (70, 74, 77, 74), (72, 69, 65, 69), (67, 71, 74, 71),
        (65, 69, 72, 74), (77, 74, 70, 74), (76, 74, 71, 67), (65, 65, 72, 72),
    ]
    for bar in range(8):
        base = bar * 4
        root, kind = chords[bar]
        # ベース: 1,3拍ルート / 2,4拍5度(バウンス)
        for i in range(4):
            m = root if i % 2 == 0 else root + 7
            s.note(base + i, 0.5, m, wave_kind="square", duty=0.5, vol=0.20)
        # 裏拍の和音スタブ(三角波)
        for i in range(4):
            s.note(base + i + 0.5, 0.22, root + 12 + kind[1], wave_kind="triangle", vol=0.09)
        # メロディ
        for i, m in enumerate(melody[bar]):
            s.note(base + i, 0.85, m, wave_kind="square", duty=0.25, vol=0.18)
        # ドラム(軽め)
        for b in (0, 2):
            s.kick(base + b, vol=0.4)
        for b in (1, 3):
            s.snare(base + b, vol=0.2)
        for i in range(8):
            s.hat(base + i * 0.5, vol=0.06)
    return s


def build_battle():
    """捕獲ミニゲーム: ニ短調 156BPM 8小節。疾走感のある戦闘ループ"""
    s = Song(156, 32, seed=44)
    chords = [(38, MIN), (38, MIN), (46, MAJ), (45, MAJ),
              (38, MIN), (48, MAJ), (46, MAJ), (45, MAJ)]  # Dm Dm Bb A / Dm C Bb A
    R = None  # 休符
    melody = [
        (74, R, 74, 72, 74, R, 77, 76), (74, 72, 74, R, 69, R, 72, 70),
        (70, R, 70, 69, 70, R, 74, 72), (73, R, 73, R, 76, 73, 69, R),
        (74, R, 74, 72, 74, R, 77, 79), (79, 77, 76, R, 76, 74, 72, R),
        (70, 72, 74, R, 77, R, 76, 74), (73, R, 76, R, 81, R, 74, R),
    ]
    for bar in range(8):
        base = bar * 4
        root, kind = chords[bar]
        # ドライブするベース(8分連打、オクターブ交互)
        for i in range(8):
            m = root if i % 2 == 0 else root + 12
            s.note(base + i * 0.5, 0.4, m, wave_kind="square", duty=0.5, vol=0.22)
        # メロディ(8分リフ)
        for i, m in enumerate(melody[bar]):
            s.note(base + i * 0.5, 0.42, m, wave_kind="square", duty=0.25, vol=0.20)
        # ドラム: 4つ打ちキック + 2,4スネア + 裏ハット
        for b in range(4):
            s.kick(base + b, vol=0.45)
        for b in (1, 3):
            s.snare(base + b, vol=0.28)
        for i in range(8):
            s.hat(base + i * 0.5 + 0.25, vol=0.07)
    return s


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(repo, "app", "src", "main", "res", "raw")
    os.makedirs(out_dir, exist_ok=True)

    songs = {
        "bgm_title": build_title(),
        "bgm_opening": build_opening(),
        "bgm_map": build_map(),
        "bgm_battle": build_battle(),
    }
    with tempfile.TemporaryDirectory() as tmp:
        for name, song in songs.items():
            wav = os.path.join(tmp, f"{name}.wav")
            ogg = os.path.join(out_dir, f"{name}.ogg")
            print(f"合成中: {name} ({song.length_sec:.1f}s)")
            song.write_wav(wav)
            # Opus in Ogg (Android API 21+ 対応。このffmpegビルドはlibvorbis非搭載のためopusを使用)
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
                 "-c:a", "libopus", "-b:a", "96k", ogg],
                check=True,
            )
            size_kb = os.path.getsize(ogg) // 1024
            print(f"  -> {ogg} ({size_kb} KB)")
    print("完了")


if __name__ == "__main__":
    sys.exit(main())
