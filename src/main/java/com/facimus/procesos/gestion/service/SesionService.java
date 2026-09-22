package com.facimus.procesos.gestion.service;

import java.time.Duration;
import java.util.List;

import com.facimus.procesos.gestion.dto.response.SesionIniciada;

/** Sesiones con refresh token: se abren en el login, se renuevan rotando el token y se cierran. */
public interface SesionService {

    /** Abre una sesion para el usuario que acaba de autenticarse y emite su primer refresh token. */
    SesionIniciada iniciar(Long empresaId, Long usuarioId);

    /**
     * Cambia un refresh token vigente por uno nuevo de la misma sesion. Un token desconocido, vencido o de una sesion
     * cerrada lanza SesionInvalidaException; uno que ya se habia usado ademas cierra la sesion, porque circula una
     * copia.
     */
    SesionIniciada renovar(String refreshToken);

    /** Cierra la sesion del codigo, si es del usuario. Sus access tokens dejan de servir aunque no hayan vencido. */
    void cerrar(Long empresaId, Long usuarioId, String codigo);

    /** Cierra la sesion a la que pertenece el refresh token, si es del usuario. */
    void cerrarConToken(Long empresaId, Long usuarioId, String refreshToken);

    /** Cierra todas las sesiones abiertas del usuario: los tokens emitidos llevan un acceso que ya no tiene. */
    void cerrarTodas(Long empresaId, Long usuarioId);

    /** Los codigos de las sesiones de todas las empresas cerradas dentro de la ventana, hasta ahora. */
    List<String> cerradasEnLosUltimos(Duration ventana);
}
