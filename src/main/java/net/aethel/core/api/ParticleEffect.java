package net.aethel.core.api;

import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.List;

/**
 * Bir particle efektinin tanimi. Skill'lerin TEK gorsel primitifi budur: skill
 * tanimlari entity, model ya da display olusturamaz (bkz. docs/DESIGN.md).
 */
public record ParticleEffect(Shape shape,
                             Motion motion,
                             Particle particle,
                             Color color,
                             double size,
                             double speed,
                             int count,
                             double radius,
                             double height,
                             int durationTicks,
                             int periodTicks,
                             double offsetY,
                             List<ParticleEffect> layers) {

    /** Particle'larin dizilecegi geometrik sekil. */
    public enum Shape {
        /** Tek nokta. */
        POINT,
        /** Yatay cember. */
        CIRCLE,
        /** Ici bos halka; CIRCLE'in ince hali. */
        RING,
        /** Dikey sarmal. */
        HELIX,
        /** Koni; alanda hasar veren yetenekler icin. */
        CONE,
        /** Iki nokta arasi dogru; isin/mizrak. */
        BEAM,
        /** Kure yuzeyi. */
        SPHERE,
        /** Yayilan dalga halkasi. */
        WAVE,
        /** Merkezden disari saçilma. */
        BURST,
        /**
         * Onde savrulan yay bicimli kesik. Kilic sallanisinin izini birakir:
         * bakis yonune dik bir duzlemde, merkezden disa dogru kalinlasan bir
         * yay cizilir. SWEEP_ATTACK ya da DUST ile en iyi sonucu verir.
         */
        SLASH,
        /**
         * Ic ice bircok yay: art arda gelen kesikler. SLASH'in agir hali,
         * bitis vuruslari icin.
         */
        SLASH_STORM
    }

    /** Seklin zaman icindeki davranisi. */
    public enum Motion {
        /** Sabit durur. */
        STATIC,
        /** Yaricap zamanla buyur. */
        EXPAND,
        /** Hedefe dogru ilerler. */
        TRAVEL,
        /** Merkez etrafinda doner. */
        ORBIT,
        /** Yukaridan asagi duser. */
        FALL
    }

    /** Tek katmanli basit efekt icin kisayol. */
    public static ParticleEffect simple(Shape shape, Particle particle, double radius, int count) {
        return new ParticleEffect(shape, Motion.STATIC, particle, Color.WHITE,
                1.0, 0.0, count, radius, 1.0, 20, 2, 0.0, List.of());
    }

    /** Efektin toplam adim sayisi; zamanlayici bunu kullanir. */
    public int steps() {
        return Math.max(1, durationTicks / Math.max(1, periodTicks));
    }
}
