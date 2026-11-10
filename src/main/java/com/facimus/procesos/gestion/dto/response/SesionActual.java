package com.facimus.procesos.gestion.dto.response;

import java.time.Duration;

/**
 * F13: una sesion abierta, tal como esta ahora: de quien es, cuanto le queda antes de cerrarse por inactividad (lo que
 * vive su refresh token sin usar) y cuanto antes de su fin, cuando hay que volver a entrar.
 */
public record SesionActual(UsuarioResponse usuario, Duration inactividad, Duration restante) {
}
