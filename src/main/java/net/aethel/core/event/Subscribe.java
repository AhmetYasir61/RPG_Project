package net.aethel.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tek parametresi bir CoreEvent olan metodu dinleyici yapar.
 * priority: kucuk once calisir. async: dinleyici sanal thread'de calistirilir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    int priority() default 100;

    /** true ise dinleyici ana thread disinda calisir; Bukkit API cagirma. */
    boolean async() default false;

    /** true ise iptal edilmis event'ler bu dinleyiciye de iletilir. */
    boolean ignoreCancelled() default false;
}
