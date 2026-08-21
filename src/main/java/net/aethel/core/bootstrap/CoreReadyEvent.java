package net.aethel.core.bootstrap;

import net.aethel.core.event.CoreEvent;

/**
 * Tum moduller acildiktan sonra yayinlanir. Vault/PAPI gibi dis kopruler ve
 * addon'lar baglanmak icin bu ani bekler.
 */
public record CoreReadyEvent(CoreContext context) implements CoreEvent {}
