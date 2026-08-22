package net.aethel.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Komut metodunun bir parametresini Brigadier argumanina baglar. Tip, parametrenin
 * Java tipinden cozulur; tab-complete ArgumentResolvers uzerinden gelir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Arg {

    /** Kullanim metninde gorunecek isim. Bos ise parametre adi kullanilir (-parameters). */
    String value() default "";

    /** true ise argumani vermemek serbesttir; verilmezse null/0 gelir. */
    boolean optional() default false;

    /** Yalnizca String parametrelerde: satirin kalanini tek arguman olarak yutar. */
    boolean greedy() default false;

    /**
     * true ise arguman namespace'li bir KIMLIKTIR ("aethel:alev_kilici") ve iki
     * nokta kabul eden tipi kullanir. Oyuncu adi gibi kimlik olmayan metinlerde
     * KAPALI birakilmalidir: kimlik tipi degeri kucuk harfe cevirir.
     */
    boolean identifier() default false;

    /**
     * Tab-complete kaynaginin adi (bkz. SuggestionRegistry). Bos ise arguman
     * icin oneri gosterilmez.
     */
    String suggests() default "";
}
