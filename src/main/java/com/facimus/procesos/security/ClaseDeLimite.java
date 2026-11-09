package com.facimus.procesos.security;

/**
 * PR 32: las clases del limite de peticiones. Cada una tiene su cubo de fichas y cuenta por lo que tiene sentido para
 * ella: lo que se puede pedir sin sesion, por la IP; lo que cambia datos, por la persona.
 */
public enum ClaseDeLimite {

    /** Entrar, renovar, dar el codigo del segundo factor y confirmar un vinculo: por IP, ademas del de cada correo. */
    AUTENTICACION,

    /** Registrar una tienda: por IP. */
    REGISTRO,

    /** Lo que manda un correo: verificar, recuperar la clave e invitar. Por IP, para que no se use para molestar. */
    CORREO,

    /** Todo lo demas que cambia algo: por persona. Leer no cuenta. */
    ESCRITURA
}
