package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.PoolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PoolRepository poolRepository;
    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private ProcesoService procesoService;

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
    @DisplayName("HU-04: crear proceso en BORRADOR con pool inicial")
    void crear_exitoso() {
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Compras")).thenReturn(false);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> {
            Proceso p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(poolRepository.save(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        Proceso result = procesoService.crear(1L, 10L, "Compras", "Proceso de compras", "Operativo");

        assertEquals(EstadoProceso.BORRADOR, result.getEstado());
        assertTrue(result.isActivo());

        ArgumentCaptor<Pool> poolCaptor = ArgumentCaptor.forClass(Pool.class);
        verify(poolRepository).save(poolCaptor.capture());
        Pool pool = poolCaptor.getValue();
        assertEquals(empresa.getNombre(), pool.getNombre());

        verify(historialCambioService).registrar(eq(result), eq(usuario), anyString());
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
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(inv -> inv.getArgument(0));

        Proceso result = procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.PUBLICADO);

        assertEquals(EstadoProceso.PUBLICADO, result.getEstado());
        verify(historialCambioService).registrar(eq(result), eq(usuario), contains("publicado"));
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
                () -> procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.BORRADOR));
    }
}
