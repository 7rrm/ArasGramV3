package org.telegram.ui.Stars;

// Reconstructed from Cherrygram 11.6.0 APK; verify against bytecode and device before release.

/* renamed from: ʇ, reason: contains not printable characters */
/* loaded from: /home/user/.cache/cherry-tools/dex/classes7.dex */
public final class CherryGiftMotion {

    /* renamed from: ʝ, reason: contains not printable characters */
    public float f1997;

    /* renamed from: Х, reason: contains not printable characters */
    public float f1998;

    /* renamed from: Ц, reason: contains not printable characters */
    public float f1999;

    /* renamed from: ك, reason: contains not printable characters */
    public float f2000;

    /* renamed from: ޟ, reason: contains not printable characters */
    public float f2001;

    /* renamed from: ࡠ, reason: contains not printable characters */
    public float f2002;

    /* renamed from: ड, reason: contains not printable characters */
    public float f2003;

    /* renamed from: ഊ, reason: contains not printable characters */
    public float f2004;

    /* renamed from: บ, reason: contains not printable characters */
    public float f2005;

    /* renamed from: ສ, reason: contains not printable characters */
    public float f2006;

    /* renamed from: ᄬ, reason: contains not printable characters */
    public float f2007;

    /* renamed from: ᆍ, reason: contains not printable characters */
    public float f2008;

    /* renamed from: ኩ, reason: contains not printable characters */
    public float f2009;

    /* renamed from: ጆ, reason: contains not printable characters */
    public float f2011;

    /* renamed from: ᗼ, reason: contains not printable characters */
    public boolean f2012;

    /* renamed from: ᚲ, reason: contains not printable characters */
    public float f2013;

    /* renamed from: ឪ, reason: contains not printable characters */
    public final float f2014;

    /* renamed from: ឳ, reason: contains not printable characters */
    public float f2015;

    /* renamed from: ᧂ, reason: contains not printable characters */
    public float f2017;

    /* renamed from: ᯎ, reason: contains not printable characters */
    public float f2019;

    /* renamed from: ᱎ, reason: contains not printable characters */
    public float f2020;

    /* renamed from: ṁ, reason: contains not printable characters */
    public final float f2021;

    /* renamed from: Ṗ, reason: contains not printable characters */
    public float f2022;

    /* renamed from: Ṟ, reason: contains not printable characters */
    public float f2023;

    /* renamed from: ἵ, reason: contains not printable characters */
    public float f2024;

    /* renamed from: Ⱟ, reason: contains not printable characters */
    public float f2025;

    /* renamed from: ィ, reason: contains not printable characters */
    public float f2026;

    /* renamed from: ㅐ, reason: contains not printable characters */
    public boolean f2027;

    /* renamed from: ㅯ, reason: contains not printable characters */
    public float f2028;

    /* renamed from: 㚄, reason: contains not printable characters */
    public float f2029;

    /* renamed from: 㝺, reason: contains not printable characters */
    public final float f2030;

    /* renamed from: 㞰, reason: contains not printable characters */
    public float f2031;

    /* renamed from: 㠩, reason: contains not printable characters */
    public float f2032;

    /* renamed from: 㠰, reason: contains not printable characters */
    public float f2033;

    /* renamed from: 㦘, reason: contains not printable characters */
    public float f2034;

    /* renamed from: 㴂, reason: contains not printable characters */
    public float f2035;

    /* renamed from: 㴈, reason: contains not printable characters */
    public float f2036;

    /* renamed from: 㵱, reason: contains not printable characters */
    public float f2037;

    /* renamed from: ዡ, reason: contains not printable characters */
    public final float[] f2010 = new float[16];

    /* renamed from: ᠰ, reason: contains not printable characters */
    public final float[] f2016 = new float[16];

    /* renamed from: ᮞ, reason: contains not printable characters */
    public float f2018 = 1.0f;

    public CherryGiftMotion(long j, int i, int i2) {
        this.f2021 = m2042(m2041(25214903917L ^ j)) * 6.2831855f;
        m2044(j, i, i2);
        m2052(0.0f, true);
        float f = (((j << 17) ^ ((j >>> 29) ^ j)) & 65535) / 65535.0f;
        this.f2030 = 6.2831855f * f;
        this.f2014 = (f - 0.5f) * 1.1f;
    }

    /* renamed from: ᆍ, reason: contains not printable characters */
    public static float m2040(float f, float f2, float f3) {
        return Math.max(f2, Math.min(f3, f));
    }

    /* renamed from: Ṟ, reason: contains not printable characters */
    public static long m2041(long j) {
        long j2 = (j ^ (j >>> 30)) * (-4658895280553007687L);
        long j3 = (j2 ^ (j2 >>> 27)) * (-7723592293110705685L);
        return j3 ^ (j3 >>> 31);
    }

    /* renamed from: 㠩, reason: contains not printable characters */
    public static float m2042(long j) {
        return (j >>> 40) / 1.6777216E7f;
    }

    /* renamed from: Х, reason: contains not printable characters */
    public void m2043(float f) {
        float fHypot = (float) Math.hypot(this.f2011, this.f2015);
        if (fHypot <= f || fHypot <= 0.0f) {
            return;
        }
        float f2 = f / fHypot;
        this.f2011 *= f2;
        this.f2015 *= f2;
    }

    /* renamed from: Ц, reason: contains not printable characters */
    public void m2044(long j, int i, int i2) {
        float f;
        float f2;
        float f3;
        float f4;
        long jM2041 = m2041(j ^ (i * (-7046029254386353131L)));
        this.f2022 = (float) ((((i * 3.141592653589793d) * 2.0d) / Math.max(1, i2)) - 1.5707963267948966d);
        int i3 = i % 3;
        if (i3 == 0) {
            f = 1.14f;
            f2 = 0.96f;
            f3 = -0.12f;
            f4 = 0.04f;
        } else if (i3 != 1) {
            f = 0.98f;
            f2 = 1.06f;
            f4 = 0.16f;
            f3 = -0.04f;
        } else {
            f2 = 0.86f;
            f4 = -0.22f;
            f3 = 0.08f;
            f = 0.88f;
        }
        long jM20412 = m2041(jM2041);
        this.f1998 = f + ((m2042(jM20412) - 0.5f) * 0.06f);
        long jM20413 = m2041(jM20412);
        this.f2023 = f2 + ((m2042(jM20413) - 0.5f) * 0.06f);
        long jM20414 = m2041(jM20413);
        this.f2003 = f4 + ((m2042(jM20414) - 0.5f) * 0.04f);
        long jM20415 = m2041(jM20414);
        this.f2002 = f3 + ((m2042(jM20415) - 0.5f) * 0.04f);
        this.f2035 = (m2042(m2041(jM20415)) * 0.14f) + 0.88f;
    }

    /* renamed from: ك, reason: contains not printable characters */
    public void m2045(float f, float f2, boolean z) {
        if (!this.f2012) {
            this.f2037 = this.f2036;
            this.f2028 = this.f1999;
            this.f2013 = 0.0f;
            this.f2007 = 0.0f;
            this.f2029 = 0.0f;
            this.f2006 = 0.0f;
            this.f2015 = 0.0f;
            this.f2011 = 0.0f;
            this.f2018 = 1.0f;
            this.f2001 = 0.0f;
            this.f2009 = 0.0f;
            this.f2005 = 0.0f;
            boolean z2 = this.f2027;
            if (!z2) {
                this.f1997 = 0.0f;
            }
            this.f2012 = true;
            if (!z2) {
                return;
            }
        }
        float fMax = Math.max(0.0f, Math.min(f, 0.05f));
        while (fMax > 0.0f) {
            float fMin = Math.min(fMax, 0.008333334f);
            fMax -= fMin;
            boolean z3 = this.f2027;
            float f3 = z3 ? this.f2004 : this.f2036;
            float f4 = z3 ? this.f2025 : this.f1999;
            float fMax2 = Math.max(0.0f, this.f1997 - fMin);
            this.f1997 = fMax2;
            float fMin2 = 1.0f - Math.min(1.0f, fMax2 / 0.65f);
            boolean z4 = this.f2027;
            float f5 = z4 ? 190.0f : (30.0f * fMin2) + 8.0f;
            float f6 = z4 ? 23.0f : (fMin2 * 5.7f) + 1.8f;
            if (!z || z4) {
                float f7 = this.f2011;
                float f8 = this.f2037;
                float f9 = f7 + ((((f3 - f8) * f5) - (f7 * f6)) * fMin);
                this.f2011 = f9;
                float f10 = this.f2015;
                float f11 = this.f2028;
                float f12 = f10 + ((((f4 - f11) * f5) - (f6 * f10)) * fMin);
                this.f2015 = f12;
                this.f2037 = f8 + (f9 * fMin);
                this.f2028 = f11 + (f12 * fMin);
            } else {
                float fExp = 1.0f - ((float) Math.exp((-fMin) / 0.03f));
                float f13 = this.f2037;
                float f14 = ((f3 - f13) * fExp) + f13;
                float f15 = this.f2028;
                float f16 = ((f4 - f15) * fExp) + f15;
                this.f2011 = (f14 - f13) / fMin;
                this.f2015 = (f16 - f15) / fMin;
                this.f2037 = f14;
                this.f2028 = f16;
            }
            float f17 = this.f2005;
            float f18 = this.f2027 ? 1.7f : 1.0f;
            float f19 = this.f2018;
            float f20 = f17 + ((((f18 - f19) * 100.0f) * fMin) - ((13.0f * f17) * fMin));
            this.f2005 = f20;
            this.f2018 = f19 + (f20 * fMin);
            float fHypot = (float) Math.hypot(this.f2011, this.f2015);
            float fMin3 = Math.min(0.28f, fHypot / Math.max(1.0f, 42.0f * f2));
            float f21 = this.f2009;
            this.f2009 = f21 + ((fMin3 - f21) * Math.min(1.0f, fMin * 14.0f));
            if (fHypot > 0.15f * f2) {
                this.f2019 = (float) Math.atan2(this.f2015, this.f2011);
            }
            float f22 = (-f2) * 0.2f;
            float f23 = 0.2f * f2;
            float fM2040 = m2040((-this.f2011) * 0.013f, f22, f23);
            float fM20402 = m2040((-this.f2015) * 0.013f, f22, f23);
            float f24 = this.f2007;
            float f25 = this.f2006;
            float f26 = f24 + ((((fM2040 - f25) * 85.0f) - (f24 * 12.0f)) * fMin);
            this.f2007 = f26;
            float f27 = this.f2013;
            float f28 = this.f2029;
            float f29 = f27 + ((((fM20402 - f28) * 85.0f) - (12.0f * f27)) * fMin);
            this.f2013 = f29;
            this.f2006 = f25 + (f26 * fMin);
            this.f2029 = f28 + (f29 * fMin);
            this.f2001 += (m2040((-this.f2011) / Math.max(1.0f, 2.0f * f2), -14.0f, 14.0f) - this.f2001) * Math.min(1.0f, fMin * 10.0f);
        }
    }

    /* renamed from: ࡠ, reason: contains not printable characters */
    public void m2046(float f, float f2, float f3) {
        this.f2027 = false;
        this.f2011 = f;
        this.f2015 = f2;
        m2043(f3);
        this.f2017 = m2040(this.f2020, -1.0f, 1.0f);
        m2049(Math.min(1.0f, ((float) Math.hypot(this.f2011, this.f2015)) / Math.max(1.0f, f3)));
    }

    /* renamed from: ड, reason: contains not printable characters */
    public void m2047() {
        this.f2012 = false;
        this.f2027 = false;
        this.f1997 = 0.0f;
    }

    /* renamed from: ዡ, reason: contains not printable characters */
    public void m2048(float f) {
        float fSin = (((float) Math.sin((f * 0.8f) + this.f2030)) * 0.012f) + 1.0f;
        float phase = f * 0.8f + this.f2030;
        for (int i = 0; i < 16; i++) {
            float angle = (float) (((i * Math.PI) * 2.0d) / 16.0d);
            float fSin2 = (((float) Math.sin((2.0f * angle) + phase)) * 0.035f) + 1.0f + (((float) Math.sin(((3.0f * angle) - (0.7f * phase)) + this.f2030)) * 0.009f);
            double d = angle;
            this.f2010[i] = ((float) Math.sin(d)) * fSin2 * fSin;
            this.f2016[i] = ((-((float) Math.cos(d))) * fSin2) / fSin;
        }
    }

    /* renamed from: ṁ, reason: contains not printable characters */
    public final void m2049(float f) {
        if (this.f1997 <= 0.0f || this.f2027) {
            this.f2017 = m2040(this.f2020, -1.0f, 1.0f);
        }
        this.f1997 = Math.max(this.f1997, (f * 0.95f) + 0.45f);
    }

    /* renamed from: Ṗ, reason: contains not printable characters */
    public void m2050(float f, float f2, float f3, float f4) {
        this.f2011 += f * f3;
        this.f2015 += f2 * f3;
        this.f2005 += f4 * 2.0f;
    }

    /* renamed from: ἵ, reason: contains not printable characters */
    public float m2051(float f) {
        float fSin = this.f2014 + (((float) Math.sin((f * 0.38f) + this.f2030)) * 0.16f);
        float fMax = Math.max(0.0f, Math.min(1.0f, (this.f2009 - 0.055f) / 0.225f));
        double d = (this.f2019 + 1.5707964f) - fSin;
        return (float) Math.toDegrees(fSin + (((float) Math.atan2(Math.sin(d), Math.cos(d))) * fMax));
    }

    /* renamed from: ィ, reason: contains not printable characters */
    public void m2052(float f, boolean z) {
        float fExp = z ? 1.0f : 1.0f - ((float) Math.exp((-f) / 0.35f));
        this.f2026 = this.f2026 + (((float) Math.atan2(Math.sin(this.f2022 - this.f2026), Math.cos(this.f2022 - this.f2026))) * fExp);
        float f2 = this.f2008;
        this.f2008 = f2 + ((this.f1998 - f2) * fExp);
        float f3 = this.f2031;
        this.f2031 = f3 + ((this.f2023 - f3) * fExp);
        float f4 = this.f2033;
        this.f2033 = f4 + ((this.f2002 - f4) * fExp);
        float f5 = this.f2034;
        this.f2034 = f5 + ((this.f2003 - f5) * fExp);
        float f6 = this.f2032;
        this.f2032 = f6 + ((this.f2035 - f6) * fExp);
    }

    /* renamed from: 㞰, reason: contains not printable characters */
    public void m2053(CherryGiftMotion c0213, float f, float f2, float f3) {
        if (f <= 0.0f || f2 <= 0.0f) {
            return;
        }
        if (this.f2027 && c0213.f2027) {
            return;
        }
        float f4 = this.f2020;
        if (f4 >= -0.15f || c0213.f2020 <= 0.15f) {
            if (f4 <= 0.15f || c0213.f2020 >= -0.15f) {
                float f5 = c0213.f2037 - this.f2037;
                float f6 = c0213.f2028 - this.f2028;
                float f7 = f + f2;
                float f8 = (f5 * f5) + (f6 * f6);
                if (f8 >= f7 * f7) {
                    return;
                }
                float fSqrt = (float) Math.sqrt(f8);
                float f9 = fSqrt > 0.001f ? f5 / fSqrt : 1.0f;
                float f10 = fSqrt > 0.001f ? f6 / fSqrt : 0.0f;
                float f11 = this.f2027 ? 0.0f : 1.0f / (f * f);
                float f12 = c0213.f2027 ? 0.0f : 1.0f / (f2 * f2);
                float f13 = f11 + f12;
                if (f13 <= 0.0f) {
                    return;
                }
                float fMax = (Math.max(0.0f, (f7 - fSqrt) - (0.01f * f7)) * 0.85f) / f13;
                float f14 = f9 * fMax;
                this.f2037 -= f14 * f11;
                float f15 = fMax * f10;
                this.f2028 -= f15 * f11;
                c0213.f2037 += f14 * f12;
                c0213.f2028 += f15 * f12;
                float f16 = c0213.f2011;
                float f17 = this.f2011;
                float f18 = c0213.f2015;
                float f19 = this.f2015;
                float f20 = ((f16 - f17) * f9) + ((f18 - f19) * f10);
                if (f20 >= 0.0f) {
                    return;
                }
                float f21 = ((-1.8f) * f20) / f13;
                float f22 = f9 * f21;
                this.f2011 = f17 - (f22 * f11);
                float f23 = f21 * f10;
                this.f2015 = f19 - (f11 * f23);
                c0213.f2011 += f22 * f12;
                c0213.f2015 += f23 * f12;
                m2043(f3);
                c0213.m2043(f3);
                float f24 = -f20;
                if (f24 > f7 * 0.75f) {
                    float fMin = Math.min(1.0f, f24 / Math.max(1.0f, f3 * 0.65f));
                    if (!this.f2027) {
                        m2049(fMin);
                    }
                    if (!c0213.f2027) {
                        c0213.m2049(fMin);
                    }
                    float f25 = (fMin * 0.16f) + 0.08f;
                    this.f2009 = Math.max(this.f2009, f25);
                    c0213.f2009 = Math.max(c0213.f2009, f25);
                    float fAtan2 = (float) Math.atan2(f10, f9);
                    this.f2019 = fAtan2;
                    c0213.f2019 = fAtan2 + 3.1415927f;
                }
            }
        }
    }

    /* renamed from: 㠰, reason: contains not printable characters */
    public void m2054(float f, float f2, float f3, float f4) {
        if (f > f2 || f3 > f4) {
            return;
        }
        float f5 = this.f2037;
        if (f5 < f || f5 > f2) {
            this.f2037 = m2040(f5, f, f2);
            this.f2011 *= -0.35f;
        }
        float f6 = this.f2028;
        if (f6 < f3 || f6 > f4) {
            this.f2028 = m2040(f6, f3, f4);
            this.f2015 *= -0.35f;
        }
        if (this.f2027) {
            this.f2004 = m2040(this.f2004, f, f2);
            this.f2025 = m2040(this.f2025, f3, f4);
        }
    }

    /* renamed from: 㦘, reason: contains not printable characters */
    public void m2055(float f, float f2) {
        if (!this.f2027) {
            this.f1997 = 0.0f;
        }
        this.f2004 = f;
        this.f2025 = f2;
        this.f2027 = true;
    }

    /* renamed from: 㴂, reason: contains not printable characters */
    public void m2056() {
        if (this.f2027) {
            this.f2020 = 2.0f;
            return;
        }
        float fMin = Math.min(1.0f, this.f1997 / 0.35f);
        float f = this.f2024;
        this.f2020 = f + ((this.f2017 - f) * fMin);
    }

    /* renamed from: 㴈, reason: contains not printable characters */
    public void m2057(float f, float f2, float f3, float f4) {
        double d = this.f2026 + (0.28f * f) + f2;
        float fSin = (float) Math.sin(d);
        float fCos = ((float) Math.cos(d)) * f3 * this.f2008 * ((((float) Math.sin((0.8f * f) + this.f2021)) * 0.02f) + 1.0f);
        float f5 = fSin * f4 * this.f2031;
        float fSin2 = (float) Math.sin(this.f2033);
        float fCos2 = (float) Math.cos(this.f2033);
        this.f2024 = fSin;
        float f6 = 1.0f / (1.0f - (fSin * 0.11f));
        this.f2036 = ((fCos * fCos2) - (f5 * fSin2)) * f6;
        this.f1999 = (((fCos * fSin2) + (f5 * fCos2)) * f6) + (this.f2034 * f4) + (((float) Math.sin((f * 1.1f) + this.f2021)) * f4 * 0.025f);
        this.f2000 = this.f2032 * ((this.f2024 * 0.16f) + 0.86f);
    }
}
