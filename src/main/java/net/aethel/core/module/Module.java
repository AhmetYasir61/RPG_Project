package net.aethel.core.module;

import net.aethel.core.bootstrap.CoreContext;

/**
 * Her modulun uyguladigi yasam dongusu sozlesmesi. Kural: onLoad icinde servis
 * ARAYUZU kaydedilir, onEnable icinde baska modullerin servisleri KULLANILIR.
 * Bu ayrim sayesinde modul yukleme sirasi tuketici tarafi ilgilendirmez.
 */
public interface Module {

    /** Config okunur, servisler ServiceRegistry'ye kaydedilir. Baska servis cagrilmaz. */
    default void onLoad(CoreContext ctx) throws Exception {}

    /** Listener, komut, repository ve zamanlanmis isler burada baglanir. */
    default void onEnable(CoreContext ctx) throws Exception {}

    /** Task iptali, listener kaldirma, cache flush. Servis deregister'i cekirdek yapar. */
    default void onDisable(CoreContext ctx) throws Exception {}

    /** Config hot-reload sonrasi cagrilir; varsayilan olarak bir sey yapmaz. */
    default void onReload(CoreContext ctx) throws Exception {}
}
