package net.aethel.core.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Modul meta verisi. ModuleManager bu anotasyondan bagimlilik grafigini kurar ve
 * topolojik sirayla yukler. depends: zorunlu, softDepends: varsa once yuklenir.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {

    /** modules.yml icindeki anahtar ile birebir ayni olmali. */
    String id();

    String name();

    String[] depends() default {};

    String[] softDepends() default {};

    /** false ise modules.yml icinde acik olsa bile calisma zamaninda kapatilamaz. */
    boolean hotDisable() default true;
}
