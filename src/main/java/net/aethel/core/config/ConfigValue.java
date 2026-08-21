package net.aethel.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir alani YAML yoluna baglar. ConfigMapper reload sirasinda alani yeniden doldurur,
 * boylece modul kodu hicbir yerde config.getString("...") cagirmak zorunda kalmaz.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigValue {

    /** Ornek: "economy.starting-balance". Bos birakilirsa alan adi kebab-case'e cevrilir. */
    String value() default "";

    /** Dosyada anahtar yoksa yazilacak aciklama satiri. */
    String comment() default "";
}
