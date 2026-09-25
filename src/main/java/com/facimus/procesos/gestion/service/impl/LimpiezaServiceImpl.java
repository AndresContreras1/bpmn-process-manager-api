package com.facimus.procesos.gestion.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.repository.ClaveIdempotenciaRepository;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.service.Limpieza;
import com.facimus.procesos.gestion.service.LimpiezaService;

/**
 * D20: el unico borrado fisico del sistema, y el unico sitio que cruza tiendas a proposito. Todo lo demas se da de
 * baja y se queda; estas tres tablas cuentan lo que ya paso, nadie las consulta hacia atras y no pertenecen al
 * negocio de ninguna tienda, asi que la limpieza no lee datos de nadie: tira filas que ya no sirven en ninguna.
 */
@Service
@Transactional
public class LimpiezaServiceImpl implements LimpiezaService {

    private final SesionRepository sesionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ClaveIdempotenciaRepository claveIdempotenciaRepository;
    private final Duration retencionSesiones;
    private final Duration retencionIdempotencia;

    public LimpiezaServiceImpl(SesionRepository sesionRepository, RefreshTokenRepository refreshTokenRepository,
            ClaveIdempotenciaRepository claveIdempotenciaRepository,
            @Value("${limpieza.retencion-sesiones}") Duration retencionSesiones,
            @Value("${limpieza.retencion-idempotencia}") Duration retencionIdempotencia,
            @Value("${jwt.expiration-seconds}") long vigenciaDelToken) {
        this.sesionRepository = sesionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.claveIdempotenciaRepository = claveIdempotenciaRepository;
        this.retencionIdempotencia = retencionIdempotencia;
        // Una sesion cerrada sigue haciendo falta mientras pueda quedar vivo un access token suyo: al arrancar,
        // RevokedSessions las vuelve a leer de la base para seguir rechazandolos. Con una retencion mas corta que
        // la vigencia del token, un reinicio justo despues de la purga dejaria entrar un token ya revocado.
        this.retencionSesiones = maximo(retencionSesiones, Duration.ofSeconds(vigenciaDelToken));
    }

    @Override
    public Limpieza limpiar() {
        // Las fechas de estas tablas se escriben con LocalDateTime.now(), en la zona de la maquina: el limite se
        // calcula igual, porque comparar contra un reloj en otra zona borraria de mas o de menos.
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteDeSesiones = ahora.minus(retencionSesiones);

        // Los tokens primero: una sesion que se queda sin ninguno se va en esta misma pasada y no en la siguiente.
        int tokens = refreshTokenRepository.borrarVencidosAntesDe(limiteDeSesiones);
        int sesiones = sesionRepository.borrarSinTokensAntesDe(limiteDeSesiones);
        int claves = claveIdempotenciaRepository.borrarAntesDe(ahora.minus(retencionIdempotencia));
        return new Limpieza(tokens, sesiones, claves);
    }

    private static Duration maximo(Duration una, Duration otra) {
        return una.compareTo(otra) >= 0 ? una : otra;
    }
}
