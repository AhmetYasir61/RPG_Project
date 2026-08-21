package net.aethel.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kok komut ya da alt komut tanimlar. Sinif uzerindeyse kok, metot uzerindeyse
 * alt komuttur. Yetki ve aciklama otomatik olarak /help ciktisina yansir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Command {

    /** Ornegin "parti" ya da "davet". Bosluk icermez. */
    String value();

    String[] aliases() default {};

    /** Bos birakilirsa kok komuttan turetilir: core.command.<kok>.<alt> */
    String permission() default "";

    /** lang dosyasindaki aciklama anahtari. */
    String descriptionKey() default "";

    /** true ise komutu yalnizca oyuncular kullanabilir (konsol reddedilir). */
    boolean playerOnly() default false;

    /**
     * Bu komutu bir ozellik anahtarina baglar. Ozellik kapaliyken komut agacta
     * GORUNMEZ: tab-complete'te cikmaz ve "bilinmeyen komut" doner — yani oyuncu
     * icin komut hic var olmamis gibi olur. Bos birakilirsa komut daima aciktir.
     */
    String feature() default "";
}
