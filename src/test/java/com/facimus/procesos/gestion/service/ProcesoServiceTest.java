package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.event.ProcesoCreado;
import com.facimus.procesos.gestion.mapper.ProcesoMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.ProcesoServiceImpl;

import org.mapstruct.factory.Mappers;
import org.mockito.Spy;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcesoServiceTest {

    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private HistorialCambioService historialCambioService;

    @Spy
    private ProcesoMapper procesoMapper = Mappers.getMapper(ProcesoMapper.class);

    @InjectMocks
    private ProcesoServiceImpl procesoService;

    private Empresa empresa;
    private Usuario usuario;
    private Proceso proceso;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme");

        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmpresa(empresa);

        proceso = new Proceso();
        proceso.setId(100L);
        proceso.setEmpresa(empresa);
        proceso.setNombre("Compras");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
    }

    @Test
    @DisplayName("HU-04: crear proceso en BORRADOR y anunciarlo para que se cree su pool inicial")
    void crear_exitoso() {
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Compras")).thenReturn(false);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> {
            Proceso p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProcesoResponse result = procesoService.crear(1L, 10L, "Compras", "Proceso de compras", "Operativo");

        assertEquals(EstadoProceso.BORRADOR, result.estado());
        assertTrue(result.activo());

        // El pool inicial lo crea el modulo de modelado al recibir este evento.
        verify(eventos).publishEvent(new ProcesoCreado(1L, 100L));
        verify(historialCambioService).registrar(argThat(creado -> creado.getId() == 100L), eq(usuario), anyString());
    }

    @Test
    @DisplayName("HU-04: nombre duplicado en empresa lanza excepcion")
    void crear_nombre_duplicado() {
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Compras")).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.crear(1L, 10L, "Compras", "desc", "cat"));

        verify(procesoRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-05: publicar cambia estado a PUBLICADO")
    void publicar_exitoso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(procesoRepository.saveAndFlush(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcesoResponse result = procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO,
                proceso.getVersion());

        assertEquals(EstadoProceso.PUBLICADO, result.estado());
        verify(historialCambioService).registrar(eq(proceso), eq(usuario), contains("publicado"));
    }

    @Test
    @DisplayName("HU-06: eliminar logico pone activo=false sin DELETE fisico")
    void eliminarLogico_exitoso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        procesoService.eliminarLogico(1L, 100L, 10L);

        assertFalse(proceso.isActivo());
        verify(procesoRepository).save(proceso);
        verify(procesoRepository, never()).delete(any(Proceso.class));
        verify(procesoRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Obtener proceso de otra empresa lanza RecursoNoEncontrado")
    void obtener_otra_empresa() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 2L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> procesoService.obtener(2L, 100L));
    }

    @Test
    void proceso_publicado_no_vuelve_a_borrador() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.BORRADOR, proceso.getVersion()));
    }
}
