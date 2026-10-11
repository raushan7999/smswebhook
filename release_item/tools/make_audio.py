"""Soundtrack for the SMS Webhook teaser, synthesised here (no samples, nothing to license), then muxed in.

    python3 make_audio.py v1.0.0    # → 6_promo_video_<ver>/soundtrack.wav + the two videos with sound

Pure Python (no numpy). Music: 112 BPM, Am–F–C–G; soft pad intro, kick/hat/bass enter with the first feature
scene, pluck arpeggio from the filters scene, final chord on the end card. Effects: whoosh on every scene
change, soft "message" chime when the SMS arrives, pops for chips and cards, a confirmation ding for
"HTTP 200 · Delivered". Loudness is normalised to −14 LUFS by ffmpeg when muxing.
"""
import array, math, os, random, subprocess, sys, wave

VER = sys.argv[1] if len(sys.argv) > 1 else "v1.0.0"
sys.argv = [sys.argv[0], VER, "vertical"]  # make_teaser reads argv at import
import make_teaser as T  # noqa: E402

SR = 44100
DUR = T.total() + 0.6
N = int(DUR * SR)
L = array.array("d", bytes(8 * N))
R = array.array("d", bytes(8 * N))
rnd = random.Random(7)
TAU = 2 * math.pi


def note(n): return 440.0 * 2 ** ((n - 69) / 12)


def add(sig, at, gain=1.0, pan=0.0):
    """Mix a mono signal (list of floats) in at time `at` seconds with 3 ms edge fades."""
    i0 = int(at * SR)
    n = len(sig)
    k = min(n // 4, int(0.003 * SR)) or 1
    gl, gr = gain * (1 - max(0.0, pan)), gain * (1 + min(0.0, pan))
    for j in range(n):
        i = i0 + j
        if i < 0: continue
        if i >= N: break
        v = sig[j]
        if j < k: v *= j / k
        elif j > n - k: v *= (n - j) / k
        L[i] += v * gl
        R[i] += v * gr


def tone(freq, sec, harmonics=(1.0,), attack=0.005, decay=None, sustain_env=False):
    n = int(sec * SR)
    out = [0.0] * n
    w = TAU * freq / SR
    na = max(1, int(attack * SR))
    for j in range(n):
        s = 0.0
        for h, amp in enumerate(harmonics, 1):
            s += amp * math.sin(w * h * j)
        if decay:
            s *= math.exp(-j / SR / decay)
        if sustain_env:  # slow swell and release for pads
            s *= min(1.0, j / na) * min(1.0, (n - j) / na)
        elif j < na:
            s *= j / na
        out[j] = s
    return out


def noise(sec, decay, lp=0.0):
    n = int(sec * SR)
    out, prev = [0.0] * n, 0.0
    for j in range(n):
        x = rnd.uniform(-1, 1)
        prev = prev * lp + x * (1 - lp)  # one-pole low-pass when lp > 0
        out[j] = prev * math.exp(-j / SR / decay)
    return out


# ---------------- music ----------------
BPM = 112
BEAT = 60 / BPM
BAR = 4 * BEAT
CHORDS = [(57, 60, 64), (53, 57, 60), (48, 52, 55), (55, 59, 62)]  # Am F C G
starts = dict(T.scene_starts())
DROP = starts["s_flow"]
ARP = starts["s_filters"]
END = starts["s_end"]

bar = 0
t = 0.0
while t < END:
    chord = CHORDS[bar % 4]
    for n_ in chord:  # pad
        add(tone(note(n_), BAR + 0.15, (1.0, 0.25, 0.08), attack=0.35, sustain_env=True), t, 0.045, pan=-0.2 + (n_ % 3) * 0.2)
    if t >= DROP - 0.01:
        root = chord[0] - 12
        for b in range(8):  # bass eighths
            add(tone(note(root), BEAT / 2 * 0.9, (1.0, 0.35), decay=0.18), t + b * BEAT / 2, 0.16)
        for b in range(4):  # kick
            k = int(0.25 * SR)
            kick = [math.sin(TAU * (50 + 90 * math.exp(-j / SR / 0.04)) * j / SR) * math.exp(-j / SR / 0.12) for j in range(k)]
            add(kick, t + b * BEAT, 0.42)
            add(noise(0.05, 0.015), t + b * BEAT + BEAT / 2, 0.07, pan=0.3)  # off-beat hat
        if t >= ARP - 0.01:
            arp = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[1] + 12]
            for s in range(8):
                add(tone(note(arp[s % 4]), 0.3, (1.0, 0.5, 0.2), decay=0.09), t + s * BEAT / 2, 0.06, pan=0.35 if s % 2 else -0.35)
    t += BAR
    bar += 1

# Final chord on the end card.
for n_ in (57, 60, 64, 69):
    add(tone(note(n_), 3.2, (1.0, 0.3, 0.1), decay=1.4), END + 0.1, 0.07)

# ---------------- effects ----------------
def whoosh(at):
    n = int(0.45 * SR)
    sig, prev = [0.0] * n, 0.0
    for j in range(n):
        u = j / n
        lp = 0.97 - 0.5 * math.sin(math.pi * u)  # opening then closing filter
        prev = prev * lp + rnd.uniform(-1, 1) * (1 - lp)
        sig[j] = prev * math.sin(math.pi * u)
    add(sig, at - 0.2, 0.5)


def pop(at, f=900):
    add(tone(f, 0.08, (1.0, 0.3), decay=0.025), at, 0.12)


def chime(at, base=76):
    for k, n_ in enumerate((base, base + 7)):
        add(tone(note(n_), 0.7, (1.0, 0.2), decay=0.25), at + k * 0.09, 0.09)


for name, s in T.scene_starts()[1:]:
    whoosh(s)

f0 = starts["s_flow"]
chime(f0 + 0.45)                      # SMS arrives
pop(f0 + 1.25, 700)                   # app icon
chime(f0 + 2.35, 81)                  # JSON request
chime(f0 + 2.95, 84)                  # HTTP 200 · Delivered
for j in range(len(T.FILTER_CHIPS)):
    pop(starts["s_filters"] + 0.7 + j * 0.16, 800 + j * 60)
for j in range(len(T.FORMATS)):
    pop(starts["s_payload"] + 0.8 + j * 0.6, 1000)
for j in range(2):
    chime(starts["s_extract"] + 1.0 + j * 0.35, 79 + j * 3)
for j in range(len(T.RELIABLE)):
    pop(starts["s_reliable"] + 0.4 + j * 0.18, 650 + j * 80)
for j in range(len(T.PRIVATE)):
    pop(starts["s_private"] + 0.8 + j * 0.15, 900 + j * 70)

# ---------------- write ----------------
peak = max(max(abs(v) for v in L), max(abs(v) for v in R)) or 1.0
scale = 0.89 / peak
pcm = array.array("h", (int(max(-1, min(1, v * scale)) * 32767) for pair in zip(L, R) for v in pair))
os.makedirs(T.OUT, exist_ok=True)
wav = os.path.join(T.OUT, "soundtrack.wav")
with wave.open(wav, "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print(wav, f"{DUR:.1f}s")

for silent, final in (("_silent_teaser-vertical-1080x1920.mp4", "teaser-vertical-1080x1920.mp4"),
                      ("_silent_promo-landscape-1920x1080.mp4", "promo-landscape-1920x1080.mp4")):
    src = os.path.join(T.OUT, silent)
    if not os.path.exists(src):
        print("skip (not rendered yet):", silent); continue
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-i", wav, "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy", "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-ar", "44100", "-c:a", "aac", "-b:a", "192k",
                    "-shortest", "-movflags", "+faststart", os.path.join(T.OUT, final)], check=True)
    print("muxed", final)
