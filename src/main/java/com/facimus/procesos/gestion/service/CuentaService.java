package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/**
 * Lo que la cuenta resuelve por correo, con enlaces de un solo uso: verificar el correo, recuperar la clave y aceptar
 * una invitacion. Cada correo sale por la cola, en la transaccion de quien lo pide: si esa se deshace, no sale.
 */
public interface CuentaService {

    /** Manda el enlace para verificar el correo del usuario; si ya lo verifico, no manda nada. */
    void pedirVerificacion(Long empresaId, Long usuarioId);

    void verificarCorreo(String token);

    /**
     * Manda el enlace para elegir una clave nueva, si el correo es de un usuario activo. Haya o no usuario, quien
     * pregunta recibe la misma respuesta: la recuperacion no dice que correos estan registrados.
     */
    void pedirRecuperacion(String email);

    /** Cambia la clave con el enlace y cierra todas las sesiones del usuario. */
    void recuperarClave(String token, String nueva);

    /** HU-02.1: invita por correo. Solo puede hacerlo quien ya verifico el suyo. */
    void invitar(Long empresaId, Long autorId, String email, RolAcceso rolAcceso);

    /** Crea el usuario de la invitacion, con su nombre y su clave, y con el correo ya verificado. */
    UsuarioResponse aceptarInvitacion(String token, String nombre, String password);

    /** D20: los enlaces que vencieron hace mas de una semana. Devuelve cuantos borro. */
    int olvidarEnlacesVencidos();
}
