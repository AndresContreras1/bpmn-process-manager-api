package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.ReservaIdempotencia;
import com.facimus.procesos.gestion.model.ClaveIdempotencia;
import com.facimus.procesos.gestion.repository.ClaveIdempotenciaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.impl.IdempotenciaServiceImpl;

/**
 * La cabecera Idempotency-Key: una clave nueva se reserva, la misma con el mismo cuerpo repite la respuesta, con otro
 * cuerpo se rechaza, y una reserva abandonada se puede retomar una sola vez.
 */
@ExtendWith(MockitoExtension.class)
class IdempotenciaServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;

    @Mock
    private ClaveIdempotenciaRepository repository;
    @Mock
    private EmpresaRepository empresaRepository;

    @InjectMocks
    private IdempotenciaServiceImpl service;

    @Test
    @DisplayName("Una clave nueva se reserva con su huella y su usuario")
    void nueva() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        when(empresaRepository.getReferenceById(EMPRESA)).thenReturn(empresa);
        ArgumentCaptor<ClaveIdempotencia> guardada = ArgumentCaptor.forClass(ClaveIdempotencia.class);
        when(repository.saveAndFlush(guardada.capture())).thenAnswer(invocacion -> {
            ClaveIdempotencia clave = invocacion.getArgument(0);
            clave.setId(40L);
            return clave;
        });

        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1")).isEqualTo(new ReservaIdempotencia.Nueva(40L));

        ClaveIdempotencia clave = guardada.getValue();
        assertThat(clave.getEmpresa()).isSameAs(empresa);
        assertThat(clave.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(clave.getClave()).isEqualTo("k-1");
        assertThat(clave.getHuella()).isEqualTo("h-1");
        assertThat(clave.getFechaCreacion()).isNotNull();
    }

    @Test
    @DisplayName("Si otra peticion la reservo un instante antes, esta queda en curso")
    void carrera() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicada"));

        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1")).isInstanceOf(ReservaIdempotencia.EnCurso.class);
    }

    @Test
    @DisplayName("La misma clave con otro cuerpo es distinta, y con el mismo y ya respondida repite la respuesta")
    void existente() {
        ClaveIdempotencia reserva = reserva(LocalDateTime.now());
        when(repository.findByUsuarioIdAndClaveAndEmpresaId(USUARIO, "k-1", EMPRESA)).thenReturn(Optional.of(reserva));

        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "otra")).isInstanceOf(ReservaIdempotencia.Distinta.class);
        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1")).isInstanceOf(ReservaIdempotencia.EnCurso.class);

        reserva.setEstado(201);
        reserva.setCuerpo("{\"id\":3}");
        reserva.setUbicacion("/api/v1/procesos/3");
        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1"))
                .isEqualTo(new ReservaIdempotencia.Repetida(201, "{\"id\":3}", "/api/v1/procesos/3"));
        verify(repository, never()).retomar(any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Una reserva de hace mas de un minuto sin respuesta se retoma, si nadie la retomo antes")
    void abandonada() {
        LocalDateTime creada = LocalDateTime.now().minus(Duration.ofMinutes(2));
        ClaveIdempotencia reserva = reserva(creada);
        when(repository.findByUsuarioIdAndClaveAndEmpresaId(USUARIO, "k-1", EMPRESA)).thenReturn(Optional.of(reserva));
        when(repository.retomar(eq(40L), eq(creada), any())).thenReturn(1, 0);

        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1")).isEqualTo(new ReservaIdempotencia.Nueva(40L));
        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1")).isInstanceOf(ReservaIdempotencia.EnCurso.class);
    }

    @Test
    @DisplayName("Una reserva de hace menos de un minuto sigue en curso: no se intenta retomar")
    void recienteSigueEnCurso() {
        when(repository.findByUsuarioIdAndClaveAndEmpresaId(USUARIO, "k-1", EMPRESA))
                .thenReturn(Optional.of(reserva(LocalDateTime.now().minusSeconds(50))));

        assertThat(service.reservar(EMPRESA, USUARIO, "k-1", "h-1")).isInstanceOf(ReservaIdempotencia.EnCurso.class);
        verify(repository, never()).retomar(any(), any(), any());
    }

    @Test
    @DisplayName("Guardar deja la respuesta en la reserva; liberar la borra; una de otra tienda no se toca")
    void guardarYLiberar() {
        ClaveIdempotencia reserva = reserva(LocalDateTime.now());
        when(repository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(reserva));

        service.guardar(EMPRESA, 40L, 201, "{}", "/api/v1/x/1");
        assertThat(reserva.getEstado()).isEqualTo(201);
        assertThat(reserva.getCuerpo()).isEqualTo("{}");
        assertThat(reserva.getUbicacion()).isEqualTo("/api/v1/x/1");

        service.liberar(EMPRESA, 40L);
        verify(repository).delete(reserva);

        service.guardar(8L, 40L, 500, "x", null);
        service.liberar(8L, 40L);
        assertThat(reserva.getEstado()).isEqualTo(201);
        verify(repository).delete(any());
    }

    private static ClaveIdempotencia reserva(LocalDateTime creada) {
        return ClaveIdempotencia.builder().id(40L).usuarioId(USUARIO).clave("k-1").huella("h-1")
                .fechaCreacion(creada).build();
    }
}
