package com.facimus.procesos.gestion.service;

import java.time.Instant;

/**
 * PR 49b: lo que se guarda de una passkey, sin nada de WebAuthn: la capa de seguridad lo traduce a los tipos de Spring
 * Security y de vuelta. {@code handle} es el user handle de su duena; los transportes van separados por comas.
 */
@SuppressWarnings("java:S6218") // un registro de bytes que solo viaja entre dos capas; nadie lo compara
public record PasskeyGuardada(byte[] credencialId, byte[] handle, byte[] clavePublica, long contadorDeFirmas,
        boolean uvInicializado, String transportes, boolean respaldoPosible, boolean respaldada,
        byte[] objetoDeAtestacion, byte[] datosDelCliente, String etiqueta, Instant creadaEn, Instant ultimoUsoEn) {
}
