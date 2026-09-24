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

import java.util.List;
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
    @Mock
    private VersionService versionService;
    @Mock
    private DiagnosticoDelModelo diagnosticoDelModelo;
    @Mock
    private InstantaneaDelModelo instantaneaDelModelo;

    @Spy
    private ProcesoMapper procesoMapper = Mappers.getMapper(ProcesoMapper.class);

    @InjectMocks
    private ProcesoServiceImpl procesoService;

    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final Instantanea INSTANTANEA = new Instantanea("{\"pools\":[]}", HUELLA);

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
    @DisplayName("HU-05: publicar deja el proceso PUBLICADO y guarda la version 1 con su huella")
    void publicar_exitoso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(diagnosticoDelModelo.errores(eq(1L), any(ProcesoResponse.class))).thenReturn(List.of());
        when(versionService.siguienteNumero(1L, 100L)).thenReturn(1);
        when(instantaneaDelModelo.tomar(eq(1L), any(ProcesoResponse.class))).thenReturn(INSTANTANEA);
        when(procesoRepository.saveAndFlush(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcesoResponse result = procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO,
                proceso.getVersion());

        assertEquals(EstadoProceso.PUBLICADO, result.estado());
        assertEquals(1, result.versionPublicada());
        assertEquals(false, result.borradorPendiente());
        assertEquals(HUELLA, proceso.getHuellaPublicada());
        verify(versionService).publicar(proceso, 1, 10L, INSTANTANEA);
        verify(historialCambioService).registrar(eq(proceso), eq(usuario), eq("Versión 1 publicada."));
    }

    @Test
    @DisplayName("R-44: con errores de diagnostico no se publica, y el 409 dice cuales son")
    void publicar_conErroresDeDiagnostico_noGuardaVersion() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(diagnosticoDelModelo.errores(eq(1L), any(ProcesoResponse.class))).thenReturn(
                List.of("E-01 Compras: El pool de la tienda no tiene lanes.",
                        "E-02 Compras: El proceso no tiene ningún evento de inicio."));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO, proceso.getVersion()));

        assertEquals("El proceso no se puede publicar: 2 errores de diagnóstico.", error.getMessage());
        assertEquals(2, error.getErrores().size());
        assertEquals(EstadoProceso.BORRADOR, proceso.getEstado());
        verify(versionService, never()).publicar(any(), anyInt(), any(), any());
        verify(procesoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("R-45: publicar de nuevo sin haber cambiado nada no crea otra version")
    void publicar_sinCambios_noGuardaOtraVersion() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        proceso.setVersionPublicada(1);
        proceso.setHuellaPublicada(HUELLA);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(diagnosticoDelModelo.errores(eq(1L), any(ProcesoResponse.class))).thenReturn(List.of());
        when(versionService.siguienteNumero(1L, 100L)).thenReturn(2);
        when(instantaneaDelModelo.tomar(eq(1L), any(ProcesoResponse.class))).thenReturn(INSTANTANEA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO, proceso.getVersion()));

        assertEquals("No hay cambios desde la versión 1.", error.getMessage());
        verify(versionService, never()).publicar(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Publicar un proceso publicado con cambios guarda la version siguiente")
    void publicar_conCambios_guardaLaVersionSiguiente() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        proceso.setVersionPublicada(1);
        proceso.setHuellaPublicada("la-huella-de-la-version-1");
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(diagnosticoDelModelo.errores(eq(1L), any(ProcesoResponse.class))).thenReturn(List.of());
        when(versionService.siguienteNumero(1L, 100L)).thenReturn(2);
        when(instantaneaDelModelo.tomar(eq(1L), any(ProcesoResponse.class))).thenReturn(INSTANTANEA);
        when(procesoRepository.saveAndFlush(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcesoResponse result = procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO,
                proceso.getVersion());

        assertEquals(2, result.versionPublicada());
        verify(versionService).publicar(proceso, 2, 10L, INSTANTANEA);
        verify(historialCambioService).registrar(eq(proceso), eq(usuario), eq("Versión 2 publicada."));
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
                () -> procesoService.obtener(2L, 100L, false));
    }

    @Test
    void proceso_publicado_no_vuelve_a_borrador() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.BORRADOR, proceso.getVersion()));
    }
}
