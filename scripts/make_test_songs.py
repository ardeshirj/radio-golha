"""Generate short test songs for Radio Golha: plucked-string melodies (Karplus-Strong) in a few Persian modes.

Usage: python3 scripts/make_test_songs.py OUT_DIR
Writes one tagged FLAC per song into OUT_DIR. The app reads singer = ARTIST, program = ALBUM, dastgah = GENRE.
Singers and programs are made up. Standard library only, so the FLAC is uncompressed ("verbatim" frames).
"""
import math
import random
import struct
import sys
from pathlib import Path

RATE = 44100

# Scale steps in cents from the tonic; 150 = three-quarter tone (koron), the sound of these modes.
MODES = {
    "Shur":     [0, 150, 300, 500, 700, 800, 1000, 1200],
    "Mahur":    [0, 200, 400, 500, 700, 900, 1100, 1200],
    "Segah":    [0, 150, 350, 500, 650, 800, 1000, 1200],
    "Homayoun": [0, 150, 400, 500, 700, 800, 1000, 1200],
    "Esfahan":  [0, 200, 300, 500, 700, 800, 1100, 1200],
}
TONIC_HZ = 146.83  # D3


def pluck(freq, seconds, bright=0.5):
    """Karplus-Strong plucked string."""
    period = max(2, int(RATE / freq))
    buf = [random.uniform(-1, 1) for _ in range(period)]
    out = []
    decay = 0.996
    for i in range(int(RATE * seconds)):
        a = buf[i % period]
        b = buf[(i + 1) % period]
        new = decay * (bright * a + (1 - bright) * b)
        buf[i % period] = new
        out.append(a)
    return out


def melody(steps, rng, bars=8):
    """A wandering melody: mostly stepwise, starts and ends on the tonic, with a drone pluck each bar."""
    notes = []
    degree = 0
    for bar in range(bars):
        notes.append((0, 1.2, True))  # low drone on the tonic
        for _ in range(rng.choice([3, 4, 6])):
            degree = max(0, min(len(steps) - 1, degree + rng.choice([-2, -1, -1, 1, 1, 2])))
            notes.append((steps[degree], rng.choice([0.25, 0.25, 0.5, 0.75]), False))
    notes.append((0, 2.5, False))
    return notes


def render(notes):
    total = sum(d for _, d, _ in notes) + 3
    mix = [0.0] * int(RATE * total)
    t = 0.0
    for cents, dur, drone in notes:
        freq = TONIC_HZ * 2 ** (cents / 1200) * (0.5 if drone else 2)
        sound = pluck(freq, min(dur + 1.5, 3.0), bright=0.45 if drone else 0.5)
        start = int(t * RATE)
        gain = 0.25 if drone else 0.5
        for i, s in enumerate(sound):
            if start + i < len(mix):
                mix[start + i] += gain * s
        if not drone:
            t += dur
    peak = max(abs(x) for x in mix) or 1
    return [x / peak * 0.8 for x in mix]


def crc8(data):
    crc = 0
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc


def crc16(data):
    crc = 0
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x8005) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc


def utf8_number(n):
    """FLAC frame numbers use UTF-8-style coding."""
    if n < 0x80:
        return bytes([n])
    payload = []
    while True:
        payload.insert(0, 0x80 | (n & 0x3F))
        n >>= 6
        lead_bits = 7 - len(payload) - 1  # free bits left in the first byte
        if n < (1 << lead_bits):
            return bytes([(0xFF << (lead_bits + 1)) & 0xFF | n] + payload)


def write_flac(path, samples, tags, block=4096):
    pcm = [max(-32768, min(32767, int(s * 32767))) for s in samples]
    streaminfo = (
        struct.pack(">HH", block, block)
        + (0).to_bytes(3, "big") + (0).to_bytes(3, "big")             # frame sizes unknown
        + ((RATE << 44) | (0 << 41) | (15 << 36) | len(pcm)).to_bytes(8, "big")  # rate, mono, 16-bit, samples
        + bytes(16)                                                     # MD5 not computed
    )
    vendor = b"radio-golha"
    comment = struct.pack("<I", len(vendor)) + vendor + struct.pack("<I", len(tags))
    for key, value in tags.items():
        entry = f"{key}={value}".encode()
        comment += struct.pack("<I", len(entry)) + entry
    out = bytearray(b"fLaC")
    out += bytes([0]) + len(streaminfo).to_bytes(3, "big") + streaminfo
    out += bytes([0x80 | 4]) + len(comment).to_bytes(3, "big") + comment  # last block: Vorbis comment
    for number, start in enumerate(range(0, len(pcm), block)):
        chunk = pcm[start:start + block]
        header = bytearray(b"\xff\xf8")                       # sync, fixed block size
        header.append((0x7 << 4) | 0x9)                       # block size in header (16-bit), 44.1 kHz
        header.append((0x0 << 4) | (0x4 << 1))                # mono, 16 bits per sample
        header += utf8_number(number) + struct.pack(">H", len(chunk) - 1)
        header.append(crc8(header))
        frame = header + b"\x02" + struct.pack(f">{len(chunk)}h", *chunk)  # verbatim subframe
        out += frame + struct.pack(">H", crc16(frame))
    path.write_bytes(bytes(out))


# (dastgah, singer, program): enough overlap that every station has more than one song, except one-song dastgahs.
SONGS = [
    ("Shur", "Test Singer A", "Test Program 1"),
    ("Mahur", "Test Singer A", "Test Program 2"),
    ("Segah", "Test Singer B", "Test Program 1"),
    ("Homayoun", "Test Singer B", "Test Program 2"),
    ("Esfahan", "Test Singer C", "Test Program 1"),
    ("Shur", "Test Singer C", "Test Program 2"),
    ("Mahur", "Test Singer B", "Test Program 1"),
    ("Homayoun", "Test Singer A", "Test Program 1"),
]


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for n, (dastgah, singer, program) in enumerate(SONGS, 1):
        rng = random.Random(n)
        random.seed(n)
        tags = {"TITLE": f"Test {n:02d} - {dastgah}", "ARTIST": singer, "ALBUM": program, "GENRE": dastgah}
        path = out / f"Test {n:02d} - Dastgah-e {dastgah}.flac"
        write_flac(path, render(melody(MODES[dastgah], rng)), tags)
        print(f"{path.name}: {singer} / {program} / {dastgah}")


if __name__ == "__main__":
    main()
