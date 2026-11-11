package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.EmpresaInvitadaResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoRecibidoResponse;
import com.facimus.procesos.gestion.mapper.ProcesoCompartidoMapper;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoCompartidoRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.ProcesoCompartidoServiceImpl;

/**
 * Compartir un proceso en solo lectura con otra empresa: con quien se puede, que queda en el historial y que solo la
 * duena del proceso lo comparte o deja de compartirlo.
 */
@ExtendWith(MockitoExtension.class)
class ProcesoCompartidoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 10L;
    private static final Long INVITADA = 2L;

    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ProcesoCompartidoRepository procesoCompartidoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ProcesoCompartidoMapper procesoCompartidoMapper;

    @InjectMocks
    private ProcesoCompartidoServiceImpl service;

    private Proceso proceso;
    private Empresa beta;
    private Usuario autor;
    private final EmpresaInvitadaResponse invitada = mock(EmpresaInvitadaResponse.class);

    @BeforeEach
    void setUp() {
        Empresa acme = Empresa.builder().id(EMPRESA).nombre("Acme").nit("900").build();
        beta = Empresa.builder().id(INVITADA).nombre("Beta").nit("800").build();
        proceso = Proceso.builder().id(PROCESO).empresa(acme).nombre("Compras").activo(true).build();
        autor = Usuario.builder().id(5L).empresa(acme).nombre("Ana").email("ana@acme.com").passwordHash("x").build();
    }

    @Test
    @DisplayName("Compartir guarda la comparticion y anota que la invitada no vera nada hasta que se publique")
    void compartirSinPublicar() {
        conProcesoYAutor();
        when(empresaRepository.findByNit("800")).thenReturn(Optional.of(beta));
        when(procesoCompartidoRepository.save(any(ProcesoCompartido.class))).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<ProcesoCompartido> guardada = ArgumentCaptor.forClass(ProcesoCompartido.class);
        when(procesoCompartidoMapper.toInvitada(guardada.capture())).thenReturn(invitada);

        assertThat(service.compartir(EMPRESA, PROCESO, 5L, "800")).isSameAs(invitada);

        ProcesoCompartido comparticion = guardada.getValue();
        assertThat(comparticion.getProceso()).isSameAs(proceso);
        assertThat(comparticion.getEmpresaInvitada()).isSameAs(beta);
        assertThat(comparticion.getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(comparticion.getFechaCompartido()).isNotNull();
        verify(historialCambioService).registrar(proceso, autor,
                "Proceso compartido en solo lectura con Beta. No verá nada hasta que el proceso se publique.");
    }

    @Test
    @DisplayName("Con una version publicada, el historial dice cual vera la invitada")
    void compartirPublicado() {
        proceso.setVersionPublicada(2);
        conProcesoYAutor();
        when(empresaRepository.findByNit("800")).thenReturn(Optional.of(beta));
        when(procesoCompartidoRepository.save(any(ProcesoCompartido.class))).thenAnswer(i -> i.getArgument(0));

        service.compartir(EMPRESA, PROCESO, 5L, "800");

        verify(historialCambioService).registrar(proceso, autor,
                "Proceso compartido en solo lectura con Beta. Verá la versión 2.");
    }

    @Test
    @DisplayName("No se comparte con un NIT desconocido, con la propia empresa ni dos veces con la misma")
    void compartirRechazos() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));

        assertThatThrownBy(() -> service.compartir(EMPRESA, PROCESO, 5L, "700"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No hay una empresa registrada con ese NIT.");

        when(empresaRepository.findByNit("900")).thenReturn(Optional.of(proceso.getEmpresa()));
        assertThatThrownBy(() -> service.compartir(EMPRESA, PROCESO, 5L, "900"))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("Un proceso no se comparte con su propia empresa.");

        when(empresaRepository.findByNit("800")).thenReturn(Optional.of(beta));
        when(procesoCompartidoRepository.existsByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(PROCESO, INVITADA, EMPRESA))
                .thenReturn(true);
        assertThatThrownBy(() -> service.compartir(EMPRESA, PROCESO, 5L, "800"))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("El proceso ya está compartido con Beta.");
        verify(procesoCompartidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un proceso que no es activo de la empresa no se comparte ni lista sus invitadas")
    void procesoAjeno() {
        assertThatThrownBy(() -> service.compartir(EMPRESA, PROCESO, 5L, "800"))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proceso no encontrado.");
        assertThatThrownBy(() -> service.listarInvitadas(EMPRESA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(empresaRepository, never()).findByNit(anyString());
    }

    @Test
    @DisplayName("Las invitadas se listan y se leen una a una; una que no esta invitada no se encuentra")
    void invitadas() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        ProcesoCompartido comparticion = comparticion();
        when(procesoCompartidoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(comparticion));
        when(procesoCompartidoRepository.findByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(PROCESO, INVITADA, EMPRESA))
                .thenReturn(Optional.of(comparticion));
        when(procesoCompartidoMapper.toInvitada(comparticion)).thenReturn(invitada);

        assertThat(service.listarInvitadas(EMPRESA, PROCESO)).containsExactly(invitada);
        assertThat(service.obtenerInvitada(EMPRESA, PROCESO, INVITADA)).isSameAs(invitada);
        assertThatThrownBy(() -> service.obtenerInvitada(EMPRESA, PROCESO, 3L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no está compartido con esa empresa.");
    }

    @Test
    @DisplayName("Dejar de compartir borra el permiso y lo anota en el historial del proceso")
    void dejarDeCompartir() {
        conProcesoYAutor();
        ProcesoCompartido comparticion = comparticion();
        when(procesoCompartidoRepository.findByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(PROCESO, INVITADA, EMPRESA))
                .thenReturn(Optional.of(comparticion));

        service.dejarDeCompartir(EMPRESA, PROCESO, INVITADA, 5L);

        verify(procesoCompartidoRepository).delete(comparticion);
        verify(historialCambioService).registrar(proceso, autor, "Se dejó de compartir el proceso con Beta.");
        assertThatThrownBy(() -> service.dejarDeCompartir(EMPRESA, PROCESO, INVITADA, 6L))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Usuario no encontrado.");
    }

    @Test
    @DisplayName("Los procesos recibidos se paginan con lo que la invitada puede ver")
    void recibidos() {
        PageRequest pagina = PageRequest.of(0, 10);
        Proceso recibido = Proceso.builder().id(20L).build();
        ProcesoRecibidoResponse respuesta = mock(ProcesoRecibidoResponse.class);
        when(procesoRepository.compartidosCon(INVITADA, pagina)).thenReturn(new PageImpl<>(List.of(recibido), pagina, 1));
        when(procesoCompartidoMapper.toRecibido(recibido)).thenReturn(respuesta);

        assertThat(service.buscarRecibidos(INVITADA, pagina).content()).containsExactly(respuesta);
    }

    private void conProcesoYAutor() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(autor));
    }

    private ProcesoCompartido comparticion() {
        return ProcesoCompartido.builder().id(30L).empresa(proceso.getEmpresa()).proceso(proceso)
                .empresaInvitada(beta).build();
    }
}
