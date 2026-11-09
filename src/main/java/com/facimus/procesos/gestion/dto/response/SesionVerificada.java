package com.facimus.procesos.gestion.dto.response;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.facimus.procesos.common.security.Factor;

/**
 * PR 49: una sesion cuya persona acaba de confirmar quien es otra vez. Alcanza para un access token nuevo de la misma
 * sesion: el codigo, el perfil, los factores y la nueva hora de verificacion, y cuanto le queda a la sesion.
 */
public record SesionVerificada(String sesion, UsuarioResponse usuario, Set<Factor> factores, Instant verificadaEn,
        Duration vidaRestante) {
}
