package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.event.ProcesoCreado;
import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.gestion.mapper.ProcesoMapper;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.ProcesoServiceImpl;

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

    @Mock
    private CarpetaRepository carpetaRepository;

    @Mock
    private CarpetaService carpetaService;

    @Mock
    private Permisos permisos;

    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private ProcesoServiceImpl procesoService;

    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final Instantanea INSTANTANEA = new Instantanea("{\"pools\":[]}", HUELLA, "<bpmn:definitions/>");

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

    @Test
    @DisplayName("HU-06: eliminar logico avisa al modelado y lo anota en el historial")
    void eliminarLogico_avisaYAnota() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));

        procesoService.eliminarLogico(1L, 100L, 10L);

        verify(eventos).publishEvent(new ProcesoEliminado(1L, 100L));
        verify(historialCambioService).registrar(proceso, usuario, "Proceso eliminado (baja logica).");
    }

    @Test
    @DisplayName("Crear en una tienda que no existe no guarda el proceso")
    void crear_sinEmpresa() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> procesoService.crear(1L, 10L, "Compras", null, null));
        verify(procesoRepository, never()).save(any());
    }

    @Test
    @DisplayName("La ficha guarda carpeta, dueno, revision y clasificacion, con las etiquetas limpias y ordenadas")
    void editarFicha_guardaLaFicha() {
        conProcesoYAutor();
        when(carpetaRepository.existsByIdAndEmpresaId(2L, 1L)).thenReturn(true);
        when(usuarioRepository.existsByIdAndEmpresaId(10L, 1L)).thenReturn(true);
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);
        LocalDate revision = LocalDate.of(2027, 3, 1);

        ProcesoResponse respuesta = procesoService.editarFicha(1L, 100L, 10L, new DatosDeFicha(2L, 10L, revision,
                Clasificacion.CONFIDENCIAL, List.of(" Pagos ", "pagos", "Envios"), proceso.getVersion()));

        assertEquals(2L, proceso.getCarpetaId());
        assertEquals(10L, proceso.getDuenoId());
        assertEquals(revision, proceso.getProximaRevision());
        assertEquals(Clasificacion.CONFIDENCIAL, proceso.getClasificacion());
        assertEquals(List.of("envios", "pagos"), proceso.getEtiquetas());
        assertEquals(List.of("envios", "pagos"), respuesta.etiquetas());
        assertEquals(2L, respuesta.carpetaId());
        verify(historialCambioService).registrar(proceso, usuario, "Ficha del repositorio editada.");
        verify(permisos, never()).exigir(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("La ficha no apunta a una carpeta ni a un dueno que no son de la tienda")
    void editarFicha_carpetaODuenoAjenos() {
        conProcesoYAutor();
        when(carpetaRepository.existsByIdAndEmpresaId(2L, 1L)).thenReturn(false);
        DatosDeFicha conCarpetaAjena = ficha(2L, null);
        DatosDeFicha conDuenoAjeno = ficha(null, 20L);

        RecursoNoEncontradoException sinCarpeta = assertThrows(RecursoNoEncontradoException.class,
                () -> procesoService.editarFicha(1L, 100L, 10L, conCarpetaAjena));
        assertEquals("Carpeta no encontrada.", sinCarpeta.getMessage());

        RecursoNoEncontradoException sinDueno = assertThrows(RecursoNoEncontradoException.class,
                () -> procesoService.editarFicha(1L, 100L, 10L, conDuenoAjeno));
        assertEquals("Usuario no encontrado.", sinDueno.getMessage());
        assertNull(proceso.getDuenoId());
        verify(procesoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Mover el proceso a otra carpeta cuando alguna tiene concesiones exige administrarlo")
    void editarFicha_moverConConcesiones() {
        conProcesoYAutor();
        when(carpetaRepository.existsByIdAndEmpresaId(2L, 1L)).thenReturn(true);
        when(permisos.tieneConcesiones(1L, Recurso.proceso(100L))).thenReturn(false);
        when(permisos.tieneConcesiones(1L, Recurso.carpeta(2L))).thenReturn(true);
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);

        procesoService.editarFicha(1L, 100L, 10L, ficha(2L, null));

        verify(permisos).exigir(1L, 10L, Accion.ADMINISTRAR, Recurso.proceso(100L));
    }

    @Test
    @DisplayName("Sacar de su carpeta un proceso con concesiones exige administrarlo; dejarlo donde esta, no")
    void editarFicha_sacarDeLaCarpeta() {
        proceso.setCarpetaId(2L);
        conProcesoYAutor();
        when(carpetaRepository.existsByIdAndEmpresaId(2L, 1L)).thenReturn(true);
        when(permisos.tieneConcesiones(1L, Recurso.proceso(100L))).thenReturn(true);
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);

        procesoService.editarFicha(1L, 100L, 10L, ficha(2L, null));
        verify(permisos, never()).tieneConcesiones(anyLong(), any());

        procesoService.editarFicha(1L, 100L, 10L, ficha(null, null));
        verify(permisos).exigir(1L, 10L, Accion.ADMINISTRAR, Recurso.proceso(100L));
        verify(permisos, never()).tieneConcesiones(1L, Recurso.carpeta(null));
        assertNull(proceso.getCarpetaId());
    }

    @Test
    @DisplayName("Mover entre carpetas sin concesiones no pide permiso de administrar")
    void editarFicha_moverSinConcesiones() {
        conProcesoYAutor();
        when(carpetaRepository.existsByIdAndEmpresaId(2L, 1L)).thenReturn(true);
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);

        procesoService.editarFicha(1L, 100L, 10L, ficha(2L, null));

        verify(permisos).tieneConcesiones(1L, Recurso.carpeta(2L));
        verify(permisos, never()).exigir(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("Una ficha con una version vieja choca")
    void editarFicha_versionVieja() {
        ReflectionTestUtils.setField(proceso, "version", 3L);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        DatosDeFicha vieja = new DatosDeFicha(null, null, null, null, List.of(), 2L);

        assertThrows(ConflictoDeVersionException.class, () -> procesoService.editarFicha(1L, 100L, 10L, vieja));
    }

    @Test
    @DisplayName("Buscar en el repositorio incluye las subcarpetas y recorta lo que el actor no puede ver")
    void buscar_conCarpetaYActor() {
        Actor actor = new Actor(10L, 1L, RolAcceso.EDITOR);
        PageRequest pagina = PageRequest.of(0, 10);
        when(carpetaService.conSubcarpetas(1L, 2L)).thenReturn(Set.of(2L, 3L));
        when(permisos.visibilidad(actor)).thenReturn(new Visibilidad(false, Set.of(3L), Set.of(), Set.of()));
        when(procesoRepository.findAll(ArgumentMatchers.<Specification<Proceso>>any(), eq(pagina)))
                .thenReturn(new PageImpl<>(List.of(proceso), pagina, 1));

        PageResponse<ProcesoResponse> respuesta = procesoService.buscar(1L, new FiltrosDeProceso(null, null, null,
                false, 2L, null, null, null, null, actor), pagina);

        assertEquals(List.of(100L), respuesta.content().stream().map(ProcesoResponse::id).toList());
    }

    @Test
    @DisplayName("Buscar sin carpeta ni actor no pregunta por subcarpetas ni por visibilidad")
    void buscar_sinCarpetaNiActor() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(procesoRepository.findAll(ArgumentMatchers.<Specification<Proceso>>any(), eq(pagina)))
                .thenReturn(new PageImpl<>(List.of(proceso), pagina, 1));

        assertEquals(1, procesoService.buscar(1L, new FiltrosDeProceso(null, null, null, false, null, null, null,
                null, null, null), pagina).content().size());
        assertEquals(1, procesoService.buscar(1L, "Com", null, null, false, pagina).content().size());
        verify(carpetaService, never()).conSubcarpetas(anyLong(), any());
        verify(permisos, never()).visibilidad(any());
    }

    @Test
    @DisplayName("Las etiquetas en uso y el historial salen de su repositorio")
    void etiquetasEHistorial() {
        when(procesoRepository.etiquetasEnUso(1L)).thenReturn(List.of("envios", "pagos"));
        assertEquals(List.of("envios", "pagos"), procesoService.etiquetasEnUso(1L));

        List<HistorialCambioResponse> historial = List.of(mock(HistorialCambioResponse.class));
        when(historialCambioService.listarPorProceso(1L, 100L)).thenReturn(historial);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        assertSame(historial, procesoService.listarHistorial(1L, 100L));
        assertSame(historial, procesoService.obtenerDetalle(1L, 100L, false).historial());
        assertEquals(100L, procesoService.obtenerDetalle(1L, 100L, false).proceso().id());
        assertThrows(RecursoNoEncontradoException.class, () -> procesoService.listarHistorial(1L, 101L));
    }

    @Test
    @DisplayName("Un proceso eliminado se lee solo pidiendo los inactivos")
    void obtener_inactivo() {
        proceso.setActivo(false);
        when(procesoRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(proceso));

        assertFalse(procesoService.obtener(1L, 100L, true).activo());
        assertThrows(RecursoNoEncontradoException.class, () -> procesoService.obtener(1L, 101L, true));
    }

    @Test
    @DisplayName("Con una version publicada, el borrador tiene cambios solo si su huella es otra")
    void borradorPendiente() {
        proceso.setVersionPublicada(1);
        proceso.setHuellaPublicada(HUELLA);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(instantaneaDelModelo.tomar(eq(1L), any())).thenReturn(INSTANTANEA,
                new Instantanea("{\"pools\":[1]}", "otra", "<bpmn:definitions/>"));

        assertFalse(procesoService.obtener(1L, 100L, false).borradorPendiente());
        assertTrue(procesoService.obtener(1L, 100L, false).borradorPendiente());
    }

    @Test
    @DisplayName("Editar los datos valida el nombre nuevo, guarda y lo anota; cambiar solo mayusculas no choca")
    void editarDatos() {
        conProcesoYAutor();
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);

        ProcesoResponse respuesta = procesoService.editarDatos(1L, 100L, 10L, "COMPRAS", "Desc", "Operativo",
                proceso.getVersion());

        assertEquals("COMPRAS", respuesta.nombre());
        assertEquals("Desc", proceso.getDescripcion());
        assertEquals("Operativo", proceso.getCategoria());
        verify(procesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(anyLong(), anyString());
        verify(historialCambioService).registrar(proceso, usuario, "Proceso editado.");
    }

    @Test
    @DisplayName("Renombrar a un nombre que ya usa otro proceso activo no procede")
    void editarDatos_nombreOcupado() {
        conProcesoYAutor();
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Ventas")).thenReturn(true);
        Long version = proceso.getVersion();

        assertThrows(ReglaNegocioException.class,
                () -> procesoService.editarDatos(1L, 100L, 10L, "Ventas", null, null, version));
        assertEquals("Compras", proceso.getNombre());
        verify(procesoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Editar con una version vieja no cambia nada")
    void editarDatos_versionVieja() {
        proceso.setModo(ModoProceso.EJECUTABLE);
        ReflectionTestUtils.setField(proceso, "version", 3L);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));

        assertThrows(ConflictoDeVersionException.class,
                () -> procesoService.editarDatos(1L, 100L, 10L, "Otro", null, null, 2L));
        assertThrows(ConflictoDeVersionException.class,
                () -> procesoService.cambiarModo(1L, 100L, 10L, ModoProceso.DOCUMENTAL, 2L));
        assertEquals("Compras", proceso.getNombre());
        assertEquals(ModoProceso.EJECUTABLE, proceso.getModo());
    }

    @Test
    @DisplayName("Cambiar el modo lo guarda y lo anota con su frase; el mismo modo no escribe nada")
    void cambiarModo() {
        proceso.setModo(ModoProceso.EJECUTABLE);
        conProcesoYAutor();
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);

        assertEquals(ModoProceso.EJECUTABLE, procesoService.cambiarModo(1L, 100L, 10L, ModoProceso.EJECUTABLE,
                proceso.getVersion()).modo());
        verify(procesoRepository, never()).saveAndFlush(any());

        assertEquals(ModoProceso.DOCUMENTAL, procesoService.cambiarModo(1L, 100L, 10L, ModoProceso.DOCUMENTAL,
                proceso.getVersion()).modo());
        verify(historialCambioService).registrar(proceso, usuario,
                "Proceso pasa a documental: se publica y se documenta, pero no abre casos.");
        procesoService.cambiarModo(1L, 100L, 10L, ModoProceso.EJECUTABLE, proceso.getVersion());
        verify(historialCambioService).registrar(proceso, usuario, "Proceso pasa a ejecutable.");
    }

    @Test
    @DisplayName("Pedir borrador para un proceso en borrador lo devuelve tal cual")
    void cambiarEstado_aBorrador() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));

        ProcesoResponse respuesta = procesoService.cambiarEstado(1L, 100L, 10L, EstadoProceso.BORRADOR,
                proceso.getVersion());

        assertEquals(EstadoProceso.BORRADOR, respuesta.estado());
        assertFalse(respuesta.borradorPendiente());
        verify(versionService, never()).publicar(any(), anyInt(), any(), any());
    }

    private void conProcesoYAutor() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, 1L)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
    }

    private DatosDeFicha ficha(Long carpetaId, Long duenoId) {
        return new DatosDeFicha(carpetaId, duenoId, null, Clasificacion.INTERNA, List.of(), proceso.getVersion());
    }
}
