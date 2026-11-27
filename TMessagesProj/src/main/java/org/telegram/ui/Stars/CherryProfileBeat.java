package org.telegram.ui.Stars;

// Reconstructed from Cherrygram 11.6.0 APK; verify against bytecode and device before release.

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/* renamed from: ᰅ, reason: contains not printable characters */
/* loaded from: /home/user/.cache/cherry-tools/dex/classes7.dex */
public final class CherryProfileBeat {

    /* renamed from: ឪ, reason: contains not printable characters */
    public static final CopyOnWriteArrayList f2500 = new CopyOnWriteArrayList();

    /* renamed from: Х, reason: contains not printable characters */
    public int f2501;

    /* renamed from: Ц, reason: contains not printable characters */
    public float f2502;

    /* renamed from: ك, reason: contains not printable characters */
    public float f2503;

    /* renamed from: ࡠ, reason: contains not printable characters */
    public int f2504;

    /* renamed from: ड, reason: contains not printable characters */
    public int f2505;

    /* renamed from: ᆍ, reason: contains not printable characters */
    public float[] f2506;

    /* renamed from: ዡ, reason: contains not printable characters */
    public float f2507;

    /* renamed from: ᠰ, reason: contains not printable characters */
    public float f2508;

    /* renamed from: ṁ, reason: contains not printable characters */
    public boolean f2509;

    /* renamed from: Ṗ, reason: contains not printable characters */
    public int f2510;

    /* renamed from: Ṟ, reason: contains not printable characters */
    public int f2511;

    /* renamed from: ἵ, reason: contains not printable characters */
    public float f2512;

    /* renamed from: ィ, reason: contains not printable characters */
    public float[] f2513;

    /* renamed from: 㝺, reason: contains not printable characters */
    public double f2514;

    /* renamed from: 㞰, reason: contains not printable characters */
    public float[] f2515;

    /* renamed from: 㠩, reason: contains not printable characters */
    public float[] f2516;

    /* renamed from: 㠰, reason: contains not printable characters */
    public float[] f2517;

    /* renamed from: 㦘, reason: contains not printable characters */
    public float[] f2518;

    /* renamed from: 㴂, reason: contains not printable characters */
    public boolean f2519;

    /* renamed from: 㴈, reason: contains not printable characters */
    public float f2520;

    /* renamed from: ᰅ$ᲇ, reason: contains not printable characters */
    public interface BeatListener {
        /* renamed from: ィ, reason: contains not printable characters */
        void mo2878(float f);
    }

    /* renamed from: Ṗ, reason: contains not printable characters */
    public static void m2871(BeatListener interfaceC0448) {
        f2500.remove(interfaceC0448);
    }

    /* renamed from: ィ, reason: contains not printable characters */
    public static void m2872(BeatListener interfaceC0448) {
        f2500.addIfAbsent(interfaceC0448);
    }

    /* renamed from: 㠩, reason: contains not printable characters */
    public static boolean m2873() {
        return !f2500.isEmpty();
    }

    /* renamed from: 㠰, reason: contains not printable characters */
    public static void m2874(float f) {
        float fMax = Float.isFinite(f) ? Math.max(0.0f, Math.min(1.0f, f)) : 0.0f;
        Iterator it = f2500.iterator();
        while (it.hasNext()) {
            ((BeatListener) it.next()).mo2878(fMax);
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:13:0x0019  */
    /* renamed from: ᆍ, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void m2875(int sampleRate, int channels, int encoding) {
        switch (encoding) {
            case 2: f2501 = 2; break;
            case 3: f2501 = 1; break;
            case 4: case 22: f2501 = 4; break;
            case 21: f2501 = 3; break;
            default: f2501 = 0;
        }
        f2519 = encoding == 4;
        f2510 = sampleRate > 0 && channels > 0 && channels <= 8 ? channels : 0;
        int rate = Math.max(1, sampleRate);
        f2511 = Math.max(1, rate / 100);
        f2520 = (float) Math.exp(-188.49555921538757d / rate);
        f2502 = 1f - (float) Math.exp(-1005.3096491487338d / rate);
        f2513 = new float[f2510]; f2506 = new float[f2510];
        f2515 = new float[f2510]; f2517 = new float[f2510];
        f2518 = new float[f2510]; f2516 = new float[f2510];
        f2505 = 0; f2504 = 0; f2514 = 0d;
        f2508 = 0f; f2503 = 0f; f2512 = 0f; f2507 = 0.03f;
        f2509 = true;
    }

    /* renamed from: 㞰, reason: contains not printable characters */
    public float m2876(ByteBuffer byteBuffer) {
        int i;
        int i2;
        float f;
        float f2;
        float fIntBitsToFloat;
        int i3 = this.f2510 * this.f2501;
        if (i3 == 0) {
            return -1.0f;
        }
        int iLimit = byteBuffer.limit() - i3;
        float fMax = -1.0f;
        for (int iPosition = byteBuffer.position(); iPosition <= iLimit; iPosition += i3) {
            int i4 = 0;
            double d = 0.0d;
            while (true) {
                i = this.f2510;
                if (i4 >= i) {
                    break;
                }
                int i5 = (this.f2501 * i4) + iPosition;
                int i6 = 0;
                int i7 = 0;
                while (true) {
                    i2 = this.f2501;
                    if (i6 >= i2) {
                        break;
                    }
                    i7 |= (byteBuffer.get(i5 + i6) & 255) << (i6 * 8);
                    i6++;
                }
                if (this.f2519) {
                    fIntBitsToFloat = Float.intBitsToFloat(i7);
                } else {
                    if (i2 == 1) {
                        f = i7 - 128;
                        f2 = 128.0f;
                    } else {
                        f = i7 << (32 - (i2 * 8));
                        f2 = 2.1474836E9f;
                    }
                    fIntBitsToFloat = f / f2;
                }
                if (!Float.isFinite(fIntBitsToFloat)) {
                    fIntBitsToFloat = 0.0f;
                }
                float fMax2 = Math.max(-1.0f, Math.min(1.0f, fIntBitsToFloat));
                float[] fArr = this.f2506;
                float f3 = this.f2520;
                float f4 = fArr[i4] + fMax2;
                float[] fArr2 = this.f2513;
                fArr[i4] = f3 * (f4 - fArr2[i4]);
                fArr2[i4] = fMax2;
                float[] fArr3 = this.f2515;
                float f5 = fArr3[i4];
                float f6 = this.f2502;
                float f7 = f5 + ((fArr[i4] - f5) * f6);
                fArr3[i4] = f7;
                float[] fArr4 = this.f2517;
                float f8 = fArr4[i4];
                float f9 = f8 + ((f7 - f8) * f6);
                fArr4[i4] = f9;
                float[] fArr5 = this.f2518;
                float f10 = fArr5[i4];
                float f11 = f10 + ((f9 - f10) * f6);
                fArr5[i4] = f11;
                float[] fArr6 = this.f2516;
                float f12 = fArr6[i4];
                fArr6[i4] = f12 + (f6 * (f11 - f12));
                d += fArr6[i4] * fArr6[i4];
                i4++;
            }
            this.f2514 += d / i;
            int i8 = this.f2504 + 1;
            this.f2504 = i8;
            if (i8 == this.f2511) {
                fMax = Math.max(fMax, m2877());
                this.f2514 = 0.0d;
                this.f2504 = 0;
            }
        }
        return fMax;
    }

    /* renamed from: 㦘, reason: contains not printable characters */
    public final float m2877() {
        float fSqrt = (float) Math.sqrt(this.f2514 / this.f2511);
        float f = this.f2512;
        float f2 = ((fSqrt - f) * 0.5f) + f;
        this.f2512 = f2;
        float f3 = this.f2503;
        this.f2503 = f3 + ((f2 - f3) * 0.04f);
        this.f2507 = Math.max(f2, this.f2507 * 0.995f);
        float f4 = this.f2508 * 0.9f;
        this.f2508 = f4;
        if (f4 < 0.001f) {
            this.f2508 = 0.0f;
        }
        int i = this.f2505;
        if (i > 0) {
            this.f2505 = i - 1;
        }
        if (this.f2512 < Math.max(0.003f, this.f2503 * 1.1f)) {
            this.f2509 = true;
        }
        if (this.f2509 && this.f2505 == 0 && this.f2512 > Math.max(0.0045f, this.f2503 * 1.5f) && this.f2512 - f > Math.max(8.0E-4f, this.f2507 * 0.025f)) {
            this.f2508 = Math.min(1.0f, ((this.f2512 - this.f2503) / Math.max(0.02f, this.f2507)) + 0.65f);
            this.f2505 = 13;
            this.f2509 = false;
        }
        return this.f2508;
    }
}
