package net.aethel.core.modules.scoreboard;

import java.util.List;

/**
 * Bir yan tablo / tab duzeni: baslik, satirlar ve gosterim kosulu.
 * Kosul bir izin adidir; bos ise herkese uygulanir.
 */
record BoardLayout(String id,
                   String condition,
                   int priority,
                   String title,
                   List<String> lines,
                   String tabHeader,
                   String tabFooter) {}
