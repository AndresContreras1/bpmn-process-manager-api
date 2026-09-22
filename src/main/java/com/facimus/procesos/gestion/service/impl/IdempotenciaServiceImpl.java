package com.facimus.procesos.gestion.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.dto.response.ReservaIdempotencia;
import com.facimus.procesos.gestion.model.ClaveIdempotencia;
import com.facimus.procesos.gestion.repository.ClaveIdempotenciaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.IdempotenciaService;

import lombok.RequiredArgsConstructor;

/**
 * Sin transaccion propia a proposito: la reserva se guarda y se confirma antes de ejecutar la peticion, y si otra
 * peticion con la misma clave la guardo primero, la restriccion unica de la base decide sin dejar esta a medias.
 */
@Service
@RequiredArgsConstructor
public class IdempotenciaServiceImpl implements IdempotenciaService {

    /** Una reserva en curso mas vieja que esto es de una peticion que nunca termino, por ejemplo por un reinicio. */
    static final Duration ABANDONO = Duration.ofMinutes(1);

    private final ClaveIdempotenciaRepository claveIdempotenciaRepository;
    private final EmpresaRepository empresaRepository;

    @Override
    public ReservaIdempotencia reservar(Long empresaId, Long usuarioId, String clave, String huella) {
        LocalDateTime ahora = LocalDateTime.now();
        Optional<ClaveIdempotencia> existente =
                claveIdempotenciaRepository.findByUsuarioIdAndClaveAndEmpresaId(usuarioId, clave, empresaId);
        if (existente.isEmpty()) {
            try {
                ClaveIdempotencia nueva = claveIdempotenciaRepository.saveAndFlush(ClaveIdempotencia.builder()
                        .empresa(empresaRepository.getReferenceById(empresaId))
                        .usuarioId(usuarioId)
                        .clave(clave)
                        .huella(huella)
                        .fechaCreacion(ahora)
                        .build());
                return new ReservaIdempotencia.Nueva(nueva.getId());
            } catch (DataIntegrityViolationException e) {
                // Otra peticion con la misma clave la reservo un instante antes
                return new ReservaIdempotencia.EnCurso();
            }
        }
        ClaveIdempotencia reserva = existente.get();
        if (!reserva.getHuella().equals(huella)) {
            return new ReservaIdempotencia.Distinta();
        }
        if (reserva.getEstado() != null) {
            return new ReservaIdempotencia.Repetida(reserva.getEstado(), reserva.getCuerpo(), reserva.getUbicacion());
        }
        boolean abandonada = reserva.getFechaCreacion().isBefore(ahora.minus(ABANDONO));
        if (abandonada
                && claveIdempotenciaRepository.retomar(reserva.getId(), reserva.getFechaCreacion(), ahora) == 1) {
            return new ReservaIdempotencia.Nueva(reserva.getId());
        }
        return new ReservaIdempotencia.EnCurso();
    }

    @Override
    @Transactional
    public void guardar(Long empresaId, Long reservaId, int estado, String cuerpo, String ubicacion) {
        claveIdempotenciaRepository.findByIdAndEmpresaId(reservaId, empresaId).ifPresent(reserva -> {
            reserva.setEstado(estado);
            reserva.setCuerpo(cuerpo);
            reserva.setUbicacion(ubicacion);
        });
    }

    @Override
    @Transactional
    public void liberar(Long empresaId, Long reservaId) {
        claveIdempotenciaRepository.findByIdAndEmpresaId(reservaId, empresaId)
                .ifPresent(claveIdempotenciaRepository::delete);
    }
}
