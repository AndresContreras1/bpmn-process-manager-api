package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.response.CodigosDeRecuperacionResponse;
import com.facimus.procesos.gestion.dto.response.InscripcionTotpResponse;
import com.facimus.procesos.gestion.dto.response.SegundoFactorResponse;

/**
 * PR 49: el segundo factor de una persona: un TOTP (RFC 6238) de su aplicacion de autenticacion y diez codigos de
 * recuperacion de un solo uso. Desde el PR 49b tambien sus passkeys. El login lo pide cuando tiene el TOTP confirmado
 * o alguna passkey, y los codigos de recuperacion salen con el primero de los dos.
 */
public interface SegundoFactorService {

    SegundoFactorResponse estado(Long empresaId, Long usuarioId);

    /** Si el login de esta persona tiene que pedirle el codigo. */
    boolean loPide(Long empresaId, Long usuarioId);

    /** Si la tienda le exige un segundo factor a este rol y la persona todavia no lo tiene. */
    boolean faltaInscribir(Long empresaId, Long usuarioId, RolAcceso rol);

    /**
     * Un secreto nuevo, sin confirmar, que reemplaza a otro sin confirmar. Con uno ya confirmado lanza
     * ReglaNegocioException: primero se quita.
     */
    InscripcionTotpResponse inscribir(Long empresaId, Long usuarioId);

    /**
     * Activa el TOTP con un codigo de la aplicacion y devuelve los diez codigos de recuperacion. Un codigo malo lanza
     * SolicitudInvalidaException; sin un TOTP por confirmar, ReglaNegocioException.
     */
    CodigosDeRecuperacionResponse confirmar(Long empresaId, Long usuarioId, String codigo);

    /**
     * El factor con que el codigo prueba quien es la persona: TOTP si es uno de su aplicacion que nadie uso, o
     * CODIGO_DE_RECUPERACION si es uno de sus codigos sin usar, que queda gastado. Vacio si no es ninguno.
     */
    Optional<Factor> verificar(Long empresaId, Long usuarioId, String codigo);

    /** Diez codigos nuevos; los que quedaban dejan de servir. Sin segundo factor lanza ReglaNegocioException. */
    CodigosDeRecuperacionResponse renovarCodigos(Long empresaId, Long usuarioId);

    /**
     * PR 49b: los diez codigos de quien acaba de activar su primer segundo factor sin la aplicacion, con una passkey.
     * Vacio si ya tenia codigos: no se le cambian por agregar otro factor.
     */
    List<String> codigosDelPrimerFactor(Long empresaId, Long usuarioId);

    /**
     * La persona quita su propia aplicacion de autenticacion. Sus codigos de recuperacion se van con ella si no le
     * quedan passkeys.
     */
    void quitarPropio(Long empresaId, Long usuarioId);

    /** PR 49b: despues de quitar un factor, los codigos de recuperacion se borran si ya no queda ninguno. */
    void olvidarCodigosSinFactor(Long empresaId, Long usuarioId);

    /**
     * Un administrador se lo quita a otra persona, que perdio el telefono y los codigos, y le cierra las sesiones: la
     * aplicacion, sus passkeys y sus codigos. El suyo no: lanza ReglaNegocioException y el propio se quita desde la
     * cuenta.
     */
    void quitarDeOtro(Long empresaId, Long autorId, Long usuarioId);

    /** Lo que se borra al anonimizar a una persona. */
    void borrarDe(Long empresaId, Long usuarioId);
}
