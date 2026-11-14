package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.event.ProcesoModificado;
import com.facimus.procesos.gestion.mapper.HistorialCambioMapper;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.HistorialCambioRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.HistorialCambioServiceImpl;

/**
 * El historial de cambios: a que tienda y recurso va cada linea, que se avisa cuando cambia un proceso, y como se
 * borra de el una persona anonimizada sin perder lo que paso.
 */
@ExtendWith(MockitoExtension.class)
class HistorialCambioServiceTest {

    private static final Long EMPRESA = 7L;

    @Mock
    private HistorialCambioRepository historialCambioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HistorialCambioMapper historialCambioMapper;
    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private HistorialCambioServiceImpl service;

    private final Empresa empresa = Empresa.builder().id(EMPRESA).nombre("Acme").build();
    private final Usuario ana = Usuario.builder().id(5L).empresa(empresa).nombre("Ana").email("ana@acme.com")
            .passwordHash("x").build();
    private final Proceso proceso = Proceso.builder().id(10L).empresa(empresa).nombre("Compras").build();

    @Test
    @DisplayName("Un cambio de un proceso se anota en el y avisa con el nombre de quien lo hizo")
    void registrarDeProceso() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(ana));
        ArgumentCaptor<HistorialCambio> linea = ArgumentCaptor.forClass(HistorialCambio.class);

        service.registrar(EMPRESA, 5L, proceso, "Proceso editado.");
        service.registrar(proceso, null, "Version 2 publicada.");

        verify(historialCambioRepository, times(2)).save(linea.capture());
        HistorialCambio primera = linea.getAllValues().getFirst();
        assertThat(primera.getProceso()).isSameAs(proceso);
        assertThat(primera.getEmpresa()).isSameAs(empresa);
        assertThat(primera.getRecursoTipo()).isEqualTo(RecursoDeHistorial.PROCESO);
        assertThat(primera.getRecursoId()).isEqualTo(10L);
        assertThat(primera.getAutor()).isSameAs(ana);
        assertThat(primera.getFechaCambio()).isNotNull();
        assertThat(primera.getDescripcionCambio()).isEqualTo("Proceso editado.");
        verify(eventos).publishEvent((Object) new ProcesoModificado(EMPRESA, 10L, "Ana"));
        verify(eventos).publishEvent((Object) new ProcesoModificado(EMPRESA, 10L, null));
    }

    @Test
    @DisplayName("Un cambio de la tienda va a la tienda de quien lo hizo, sin proceso; sin esa persona no se anota")
    void registrarDeTienda() {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(ana));
        ArgumentCaptor<HistorialCambio> linea = ArgumentCaptor.forClass(HistorialCambio.class);

        service.registrarDeTienda(EMPRESA, 5L, RecursoDeHistorial.ROL, 3L, "Rol creado.");

        verify(historialCambioRepository).save(linea.capture());
        assertThat(linea.getValue().getEmpresa()).isSameAs(empresa);
        assertThat(linea.getValue().getProceso()).isNull();
        assertThat(linea.getValue().getRecursoTipo()).isEqualTo(RecursoDeHistorial.ROL);
        assertThat(linea.getValue().getRecursoId()).isEqualTo(3L);
        assertThat(linea.getValue().getDescripcionCambio()).isEqualTo("Rol creado.");
        assertThatThrownBy(() -> service.registrarDeTienda(EMPRESA, 6L, RecursoDeHistorial.ROL, 3L, "x"))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Usuario no encontrado.");
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Anonimizar cambia el nombre entre comillas de las lineas de la persona y su correo en todas")
    void seudonimizar() {
        HistorialCambio sobreElla = linea("Usuario \"Ana\" con rol \"EDITOR\".");
        HistorialCambio conSuCorreo = linea("Invitación por correo a ana@acme.com con rol EDITOR.");
        when(historialCambioRepository.findAllByEmpresaIdAndRecursoTipoAndRecursoId(EMPRESA, RecursoDeHistorial.USUARIO,
                5L)).thenReturn(List.of(sobreElla));
        when(historialCambioRepository.findAllByEmpresaIdAndDescripcionCambioContaining(EMPRESA, "ana@acme.com"))
                .thenReturn(List.of(conSuCorreo));

        service.seudonimizarUsuario(EMPRESA, 5L, "Persona anonimizada $5", "ana@acme.com",
                "anonimizada-5@anonimo.invalid");

        assertThat(sobreElla.getDescripcionCambio())
                .isEqualTo("Usuario \"Persona anonimizada $5\" con rol \"EDITOR\".");
        assertThat(conSuCorreo.getDescripcionCambio())
                .isEqualTo("Invitación por correo a anonimizada-5@anonimo.invalid con rol EDITOR.");
    }

    @Test
    @DisplayName("El historial de un proceso y el de la tienda salen del mapper, el mas nuevo primero")
    void listar() {
        HistorialCambio una = linea("Proceso creado.");
        List<HistorialCambioResponse> respuestas = List.of(mock(HistorialCambioResponse.class));
        when(historialCambioRepository.findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDescIdDesc(10L, EMPRESA))
                .thenReturn(List.of(una));
        when(historialCambioMapper.toResponses(List.of(una))).thenReturn(respuestas);
        PageRequest pagina = PageRequest.of(0, 20);
        HistorialCambioResponse respuesta = mock(HistorialCambioResponse.class);
        when(historialCambioRepository.findAllByEmpresaIdOrderByFechaCambioDescIdDesc(EMPRESA, pagina))
                .thenReturn(new PageImpl<>(List.of(una), pagina, 1));
        when(historialCambioMapper.toResponse(una)).thenReturn(respuesta);

        assertThat(service.listarPorProceso(EMPRESA, 10L)).isSameAs(respuestas);
        assertThat(service.listarDeLaTienda(EMPRESA, pagina).content()).containsExactly(respuesta);
    }

    private HistorialCambio linea(String descripcion) {
        return HistorialCambio.builder().empresa(empresa).recursoTipo(RecursoDeHistorial.USUARIO).recursoId(5L)
                .descripcionCambio(descripcion).build();
    }
}
