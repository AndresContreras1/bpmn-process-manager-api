package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.ProcesoDelRolResponse;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.mapper.RolProcesoMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.impl.RolProcesoServiceImpl;

@ExtendWith(MockitoExtension.class)
class RolProcesoServiceTest {

    @Mock
    private RolProcesoRepository rolProcesoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsoDeRoles usoDeRoles;

    @Spy
    private RolProcesoMapper rolProcesoMapper = Mappers.getMapper(RolProcesoMapper.class);

    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private RolProcesoServiceImpl rolProcesoService;

    private Empresa empresa;
    private RolProceso rol;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        rol = new RolProceso();
        rol.setId(5L);
        rol.setEmpresa(empresa);
        rol.setNombre("Analista");
        rol.setActivo(true);
    }

    @Test
    @DisplayName("HU-17: crear rol con nombre unico")
    void crear_exitoso() {
        when(rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Auditor")).thenReturn(false);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));

        RolProcesoVistaResponse result = rolProcesoService.crear(1L, 10L, "Auditor", "Revisa procesos");

        assertEquals("Auditor", result.nombre());
        assertFalse(result.enUso());
    }

    @Test
    @DisplayName("HU-19: eliminar rol en uso lanza excepcion (regla 13)")
    void eliminar_rol_en_uso() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(usoDeRoles.procesosQueLoUsan(1L, 5L)).thenReturn(List.of("Compras"));

        assertThrows(ReglaNegocioException.class,
                () -> rolProcesoService.eliminar(1L, 10L, 5L));

        verify(rolProcesoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("HU-19: eliminar rol sin uso desactiva correctamente")
    void eliminar_rol_sin_uso() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(usoDeRoles.procesosQueLoUsan(1L, 5L)).thenReturn(Collections.emptyList());
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));

        rolProcesoService.eliminar(1L, 10L, 5L);

        assertFalse(rol.isActivo());
        verify(rolProcesoRepository).save(rol);
    }

    @Test
    @DisplayName("Buscar con nombre filtra por el; sin nombre trae todos, cada uno con cuantos procesos lo usan")
    void buscar_conYSinNombre() {
        RolProceso otro = new RolProceso();
        otro.setId(6L);
        otro.setEmpresa(empresa);
        otro.setNombre("Bodega");
        PageRequest pagina = PageRequest.of(0, 10);
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(1L, "an", pagina))
                .thenReturn(new PageImpl<>(List.of(rol), pagina, 1));
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(1L, pagina))
                .thenReturn(new PageImpl<>(List.of(rol, otro), pagina, 2));
        when(usoDeRoles.contarProcesosPorRol(eq(1L), anyCollection())).thenReturn(Map.of(5L, 3L));

        List<RolProcesoVistaResponse> filtrados = rolProcesoService.buscar(1L, "an", pagina).content();
        List<RolProcesoVistaResponse> todos = rolProcesoService.buscar(1L, " ", pagina).content();

        assertEquals(List.of("Analista"), filtrados.stream().map(RolProcesoVistaResponse::nombre).toList());
        assertEquals(3L, filtrados.getFirst().procesosQueLoUsan());
        assertTrue(filtrados.getFirst().enUso());
        assertEquals(2, todos.size());
        assertEquals(0L, todos.get(1).procesosQueLoUsan());
        assertFalse(todos.get(1).enUso());
        verify(usoDeRoles).contarProcesosPorRol(1L, List.of(5L, 6L));
    }

    @Test
    @DisplayName("HU-17: un nombre que ya tiene otro rol activo no se repite, y se anota el rol creado")
    void crear_nombreRepetidoYHistorial() {
        when(rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Analista")).thenReturn(true);
        ReglaNegocioException repetido = assertThrows(ReglaNegocioException.class,
                () -> rolProcesoService.crear(1L, 10L, "Analista", null));
        assertEquals("Ya existe un rol de proceso con el nombre \"Analista\" en esta empresa.", repetido.getMessage());

        assertThrows(RecursoNoEncontradoException.class, () -> rolProcesoService.crear(1L, 10L, "Auditor", null));

        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> {
            RolProceso guardado = inv.getArgument(0);
            guardado.setId(8L);
            return guardado;
        });
        RolProcesoVistaResponse creado = rolProcesoService.crear(1L, 10L, "Auditor", "Revisa");
        assertEquals(8L, creado.id());
        assertEquals("Revisa", creado.descripcion());
        verify(historialCambioService).registrarDeTienda(1L, 10L, RecursoDeHistorial.ROL, 8L,
                "Rol de proceso \"Auditor\" creado.");
    }

    @Test
    @DisplayName("Obtener trae el rol con su uso y los procesos que lo usan; uno inactivo no se encuentra")
    void obtener_conProcesos() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(usoDeRoles.contarProcesos(1L, 5L)).thenReturn(2L);
        List<ProcesoDelRolResponse> procesos = List.of(mock(ProcesoDelRolResponse.class));
        when(usoDeRoles.procesosDelRol(1L, 5L)).thenReturn(procesos);

        RolProcesoVistaResponse respuesta = rolProcesoService.obtener(1L, 5L);

        assertEquals("Analista", respuesta.nombre());
        assertEquals(2L, respuesta.procesosQueLoUsan());
        assertSame(procesos, respuesta.procesos());
        assertThrows(RecursoNoEncontradoException.class, () -> rolProcesoService.obtener(1L, 6L));
    }

    @Test
    @DisplayName("Editar renombra y lo anota como renombrado; con el mismo nombre se anota como editado")
    void editar_renombraOEdita() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(rolProcesoRepository.saveAndFlush(rol)).thenReturn(rol);
        when(usoDeRoles.contarProcesos(1L, 5L)).thenReturn(1L);

        RolProcesoVistaResponse respuesta = rolProcesoService.editar(1L, 10L, 5L, "Analista senior", "Revisa todo",
                rol.getVersion());

        assertEquals("Analista senior", rol.getNombre());
        assertEquals("Revisa todo", rol.getDescripcion());
        assertEquals(1L, respuesta.procesosQueLoUsan());
        verify(historialCambioService).registrarDeTienda(1L, 10L, RecursoDeHistorial.ROL, 5L,
                "Rol de proceso \"Analista\" renombrado a \"Analista senior\".");

        rolProcesoService.editar(1L, 10L, 5L, "Analista senior", "Otra", rol.getVersion());
        verify(historialCambioService).registrarDeTienda(1L, 10L, RecursoDeHistorial.ROL, 5L,
                "Rol de proceso \"Analista senior\" editado.");
    }

    @Test
    @DisplayName("Editar con un nombre de otro rol, o con una version vieja, no cambia nada")
    void editar_rechazos() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(1L, "Bodega", 5L))
                .thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> rolProcesoService.editar(1L, 10L, 5L, "Bodega", null, rol.getVersion()));
        ReflectionTestUtils.setField(rol, "version", 3L);
        assertThrows(ConflictoDeVersionException.class,
                () -> rolProcesoService.editar(1L, 10L, 5L, "Otro", null, 2L));

        assertEquals("Analista", rol.getNombre());
        verify(rolProcesoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(historialCambioService);
    }

    @Test
    @DisplayName("Eliminar un rol sin uso lo anota en el historial")
    void eliminar_anota() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(usoDeRoles.procesosQueLoUsan(1L, 5L)).thenReturn(List.of());

        rolProcesoService.eliminar(1L, 10L, 5L);

        verify(historialCambioService).registrarDeTienda(1L, 10L, RecursoDeHistorial.ROL, 5L,
                "Rol de proceso \"Analista\" eliminado.");
    }
}
