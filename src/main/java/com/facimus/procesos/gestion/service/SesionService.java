package com.facimus.procesos.gestion.service;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.response.SesionActual;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.SesionVerificada;

/** Sesiones con refresh token: se abren en el login, se renuevan rotando el token y se cierran. */
public interface SesionService {

    /** Abre una sesion para el usuario que acaba de entrar con su contrasena y emite su primer refresh token. */
    default SesionIniciada iniciar(Long empresaId, Long usuarioId) {
        return iniciar(empresaId, usuarioId, EnumSet.of(Factor.PASSWORD));
    }

    /** PR 49: abre una sesion con los factores con que la persona probo quien es. */
    SesionIniciada iniciar(Long empresaId, Long usuarioId, Set<Factor> factores);

    /**
     * PR 49: la persona de la sesion acaba de confirmar quien es otra vez. Lo que pide una verificacion reciente
     * vuelve a estar a su alcance; lo devuelto alcanza para un access token nuevo de la misma sesion. Una sesion
     * cerrada, o de otra persona, lanza SesionInvalidaException.
     */
    default SesionVerificada reverificar(Long empresaId, Long usuarioId, String codigo) {
        return reverificar(empresaId, usuarioId, codigo, Factor.PASSWORD);
    }

    /** Lo mismo, con el factor con que la persona lo confirmo: queda entre los de la sesion. */
    SesionVerificada reverificar(Long empresaId, Long usuarioId, String codigo, Factor factor);

    /**
     * Cambia un refresh token vigente por uno nuevo de la misma sesion. Un token desconocido, vencido o de una sesion
     * cerrada lanza SesionInvalidaException; uno que ya se habia usado ademas cierra la sesion, porque circula una
     * copia.
     */
    SesionIniciada renovar(String refreshToken);

    /**
     * F13: la sesion del codigo como esta ahora, sin renovarla: de quien es y cuanto le queda por inactividad y hasta su
     * fin. Una sesion cerrada, o de otra persona, lanza SesionInvalidaException.
     */
    SesionActual actual(Long empresaId, Long usuarioId, String codigo);

    /** Cierra la sesion del codigo, si es del usuario. Sus access tokens dejan de servir aunque no hayan vencido. */
    void cerrar(Long empresaId, Long usuarioId, String codigo);

    /**
     * Cierra la sesion a la que pertenece el refresh token. Tenerlo es la prueba de ser su dueno: es lo que permite
     * salir aunque el access token ya haya vencido.
     */
    void cerrarConToken(String refreshToken);

    /** Cierra todas las sesiones abiertas del usuario: los tokens emitidos llevan un acceso que ya no tiene. */
    void cerrarTodas(Long empresaId, Long usuarioId);

    /** Los codigos de las sesiones de todas las empresas cerradas dentro de la ventana, hasta ahora. */
    List<String> cerradasEnLosUltimos(Duration ventana);
}
