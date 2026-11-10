package com.facimus.procesos.gestion.service;

/**
 * PR 49b: una persona vista por WebAuthn. {@code email} es el nombre con que su dispositivo muestra la cuenta y
 * {@code handle}, su user handle, vacio hasta su primera passkey.
 */
@SuppressWarnings("java:S6218") // como PasskeyGuardada: solo viaja entre dos capas
public record PersonaWebAuthn(Long empresaId, Long usuarioId, String email, String nombre, byte[] handle) {
}
