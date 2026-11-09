package com.facimus.procesos.common.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.facimus.procesos.common.model.RolAcceso;

/**
 * Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. sesion es el codigo de la sesion que
 * emitio el token (claim sid): cuando la sesion se cierra, el token deja de servir. debeCambiarClave viaja tambien
 * en el token, asi que el filtro que lo exige no consulta la base (D17).
 *
 * <p>PR 49: segundoFactorPendiente es el login a medias de quien tiene un segundo factor: dio la contrasena y todavia
 * no el codigo. Ese token solo sirve para darlo, y su sesion todavia no existe en la base. debeInscribirSegundoFactor
 * es quien entro en una tienda que se lo exige y todavia no lo tiene: solo puede inscribirlo.
 */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email, String sesion,
        boolean debeCambiarClave, boolean segundoFactorPendiente, boolean debeInscribirSegundoFactor) {

    /** La identidad de una sesion completa. */
    public ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email, String sesion,
            boolean debeCambiarClave) {
        this(usuarioId, empresaId, rol, email, sesion, debeCambiarClave, false, false);
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }

    /** La misma persona, con el login a medias: falta el segundo factor. */
    public ApiPrincipal pendienteDelSegundoFactor() {
        return new ApiPrincipal(usuarioId, empresaId, rol, email, sesion, debeCambiarClave, true,
                debeInscribirSegundoFactor);
    }

    /** La misma persona, en una tienda que le exige un segundo factor que no tiene. */
    public ApiPrincipal conInscripcionObligatoria() {
        return new ApiPrincipal(usuarioId, empresaId, rol, email, sesion, debeCambiarClave, segundoFactorPendiente,
                true);
    }
}
