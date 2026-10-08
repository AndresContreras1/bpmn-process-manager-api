package com.facimus.procesos.gestion.service.impl;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.PropositoDeEnlace;

/**
 * Lo que la cola guarda de un correo de la cuenta. No lleva el token: el enlace se crea al mandarlo, asi que un token en
 * claro nunca queda escrito en la base.
 *
 * @param usuarioId de quien es el enlace; una invitacion todavia no tiene usuario
 * @param rolAcceso con que rol entra quien acepte la invitacion
 * @param autorId quien invito
 * @param idioma en que se escribe el correo: es, en o fr
 */
record PedidoDeCorreo(PropositoDeEnlace proposito, Long empresaId, Long usuarioId, String email, RolAcceso rolAcceso,
        Long autorId, String idioma) {

    static final String TIPO = "correo-de-cuenta";
}
