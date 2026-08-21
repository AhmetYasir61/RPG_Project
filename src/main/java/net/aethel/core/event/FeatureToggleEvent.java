package net.aethel.core.event;

/**
 * Bir ozellik acilip kapatildiginda yayinlanir. Moduller bu olayi dinleyerek
 * kendi ic durumlarini (onbellek, gorev, HUD katmani) tazeler.
 */
public record FeatureToggleEvent(String key, boolean enabled) implements CoreEvent {}
