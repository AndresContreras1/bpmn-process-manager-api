package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.event.ProcesoPublicado;
import com.facimus.procesos.gestion.mapper.ProcesoMapper;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.ProcesoServiceImpl;

/**
 * PR 64: lo que el proceso pone para publicar con aprobacion (D32): congelar el borrador con las condiciones de
 * publicar, decir si lo congelado todavia se publicaria, publicarlo por el camino de siempre y la recertificacion.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicacionConAprobacionTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 100L;
    private static final Long ANA = 10L;
    private static final Long BETO = 11L;
    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final Instantanea INSTANTANEA = new Instantanea("{\"pools\":[]}", HUELLA, "<bpmn:definitions/>");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

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
    @Mock
    private PoliticaDePublicacionService politicaDePublicacion;
    @Mock
    private Clock reloj;

    @InjectMocks
    private ProcesoServiceImpl procesoService;

    private Usuario ana;
    private Proceso proceso;

    @BeforeEach
    void preparar() {
        Empresa empresa = Empresa.builder().id(EMPRESA).nombre("Acme").build();
        ana = Usuario.builder().id(ANA).nombre("Ana").empresa(empresa).build();
        proceso = Proceso.builder().id(PROCESO).empresa(empresa).nombre("Compras").estado(EstadoProceso.PUBLICADO)
                .versionPublicada(2).huellaPublicada("la-huella-de-la-2").activo(true).build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(ANA, EMPRESA)).thenReturn(Optional.of(ana));
        when(procesoRepository.saveAndFlush(any(Proceso.class))).thenAnswer(llamada -> llamada.getArgument(0));
        when(diagnosticoDelModelo.errores(eq(EMPRESA), any(ProcesoResponse.class))).thenReturn(List.of());
        when(versionService.siguienteNumero(EMPRESA, PROCESO)).thenReturn(3);
        when(instantaneaDelModelo.tomar(eq(EMPRESA), any(ProcesoResponse.class))).thenReturn(INSTANTANEA);
        when(reloj.instant()).thenReturn(Instant.parse("2026-11-12T10:00:00Z"));
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private BorradorCongelado congelado() {
        return new BorradorCongelado(3, 2, INSTANTANEA);
    }

    @Test
    @DisplayName("D32: con la aprobacion encendida nadie publica directo, y el 409 dice que hay que pedir una revision")
    void publicarDirectoConAprobacion() {
        when(politicaDePublicacion.conAprobacion(EMPRESA)).thenReturn(true);
        Long version = proceso.getVersion();

        assertThatThrownBy(() -> procesoService.cambiarEstado(EMPRESA, PROCESO, ANA, EstadoProceso.PUBLICADO,
                version)).isInstanceOf(ReglaNegocioException.class).hasMessage("Esta organización publica con "
                        + "aprobación: hay que pedir una revisión del proceso, y lo publica la última aprobación que "
                        + "haga falta.");
        verify(versionService, never()).publicar(any(), anyInt(), any(), any());
        verify(diagnosticoDelModelo, never()).errores(eq(EMPRESA), any(ProcesoResponse.class));
    }

    @Test
    @DisplayName("congelar toma la instantanea con el numero siguiente y recuerda la vigente de ese momento")
    void congelar() {
        BorradorCongelado congelado = procesoService.congelar(EMPRESA, PROCESO);

        assertThat(congelado).isEqualTo(new BorradorCongelado(3, 2, INSTANTANEA));
        verify(instantaneaDelModelo).tomar(eq(EMPRESA), argThat(respuesta -> respuesta
                .versionPublicada() == 3 && respuesta.estado() == EstadoProceso.PUBLICADO));
        verify(versionService, never()).publicar(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("congelar pide lo mismo que publicar: el diagnostico limpio y algo distinto de la vigente")
    void congelarConLasCondicionesDePublicar() {
        when(diagnosticoDelModelo.errores(eq(EMPRESA), any(ProcesoResponse.class))).thenReturn(List.of("E-01 x"));
        assertThatThrownBy(() -> procesoService.congelar(EMPRESA, PROCESO)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("No se puede pedir la revisión: 1 error de diagnóstico.");

        when(diagnosticoDelModelo.errores(eq(EMPRESA), any(ProcesoResponse.class))).thenReturn(List.of());
        proceso.setHuellaPublicada(HUELLA);
        assertThatThrownBy(() -> procesoService.congelar(EMPRESA, PROCESO)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("No hay cambios desde la versión 2.");

        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> procesoService.congelar(EMPRESA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("lo congelado se publica mientras la vigente sea la misma y su diagnostico siga limpio")
    void sinImpedimento() {
        when(diagnosticoDelModelo.errores(EMPRESA, PROCESO, INSTANTANEA.definicion())).thenReturn(List.of());

        assertThat(procesoService.impedimento(EMPRESA, PROCESO, congelado())).isEmpty();
    }

    @Test
    @DisplayName("si se publico otra version despues de pedirla, lo aprobado ya no es la diferencia que se vio")
    void otraVersionPublicada() {
        when(versionService.siguienteNumero(EMPRESA, PROCESO)).thenReturn(4);
        proceso.setVersionPublicada(3);

        assertThat(procesoService.impedimento(EMPRESA, PROCESO, congelado())).contains("La versión vigente cambió "
                + "desde que se pidió la revisión (era la 2 y ahora es la 3): hay que pedirla de nuevo.");
        verify(diagnosticoDelModelo, never()).errores(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("si se retiro la vigente, aunque el numero siguiente sea el mismo, tampoco")
    void vigenteRetirada() {
        assertThat(procesoService.impedimento(EMPRESA, PROCESO, new BorradorCongelado(3, null, INSTANTANEA)))
                .contains("La versión vigente cambió desde que se pidió la revisión (era ninguna y ahora es la 2): "
                        + "hay que pedirla de nuevo.");

        proceso.setVersionPublicada(null);
        assertThat(procesoService.impedimento(EMPRESA, PROCESO, congelado())).contains("La versión vigente cambió "
                + "desde que se pidió la revisión (era la 2 y ahora es ninguna): hay que pedirla de nuevo.");
        // Sin vigente al pedirla y sin vigente hoy, sigue en pie
        assertThat(procesoService.impedimento(EMPRESA, PROCESO, new BorradorCongelado(3, null, INSTANTANEA)))
                .isEmpty();
    }

    @Test
    @DisplayName("si el diagnostico de lo congelado ya no esta limpio, dice cuales errores, hasta 2000 caracteres")
    void diagnosticoSucio() {
        when(diagnosticoDelModelo.errores(EMPRESA, PROCESO, INSTANTANEA.definicion())).thenReturn(List.of(
                "E20 Llama a un proceso dado de baja.", "E21 Las llamadas son circulares."));
        assertThat(procesoService.impedimento(EMPRESA, PROCESO, congelado())).contains("El diagnóstico ya no está "
                + "limpio: E20 Llama a un proceso dado de baja. E21 Las llamadas son circulares.");

        String prefijo = "El diagnóstico ya no está limpio: ";
        when(diagnosticoDelModelo.errores(EMPRESA, PROCESO, INSTANTANEA.definicion()))
                .thenReturn(List.of("x".repeat(2000 - prefijo.length())));
        assertThat(procesoService.impedimento(EMPRESA, PROCESO, congelado())).hasValueSatisfying(motivo -> assertThat(
                motivo).hasSize(2000).endsWith("x"));

        when(diagnosticoDelModelo.errores(EMPRESA, PROCESO, INSTANTANEA.definicion()))
                .thenReturn(List.of("x".repeat(2001 - prefijo.length())));
        assertThat(procesoService.impedimento(EMPRESA, PROCESO, congelado())).hasValueSatisfying(motivo -> assertThat(
                motivo).hasSize(2000).endsWith("x..."));
    }

    @Test
    @DisplayName("publicar lo congelado guarda esa instantanea como la version, por el camino de publicar")
    void publicarCongelado() {
        when(diagnosticoDelModelo.errores(EMPRESA, PROCESO, INSTANTANEA.definicion())).thenReturn(List.of());

        Optional<String> impedimento = procesoService.publicarCongelado(EMPRESA, PROCESO, ANA, congelado(), 40L);

        assertThat(impedimento).isEmpty();
        verify(versionService).publicar(proceso, 3, ANA, INSTANTANEA);
        assertThat(proceso.getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
        assertThat(proceso.getVersionPublicada()).isEqualTo(3);
        assertThat(proceso.getHuellaPublicada()).isEqualTo(HUELLA);
        verify(historialCambioService).registrar(proceso, ana, "Versión 3 publicada.");
        verify(eventos).publishEvent(new ProcesoPublicado(EMPRESA, PROCESO, 3, "Ana"));
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("version", 3);
        detalle.put("solicitud", 40L);
        verify(auditoria).registrar(EMPRESA, ANA, "PROCESO_PUBLICADO", "PROCESO", PROCESO, detalle);
        // Lo congelado no se vuelve a tomar del borrador de hoy
        verify(instantaneaDelModelo, never()).tomar(eq(EMPRESA), any(ProcesoResponse.class));
    }

    @Test
    @DisplayName("si lo congelado ya no se publica, no se guarda nada y se dice por que")
    void publicarCongeladoImpedido() {
        when(versionService.siguienteNumero(EMPRESA, PROCESO)).thenReturn(4);

        assertThat(procesoService.publicarCongelado(EMPRESA, PROCESO, ANA, congelado(), 40L)).isPresent();

        verify(versionService, never()).publicar(any(), anyInt(), any(), any());
        verify(procesoRepository, never()).saveAndFlush(any());
        assertThat(proceso.getVersionPublicada()).isEqualTo(2);
    }

    @Test
    @DisplayName("su dueno confirma que sigue vigente: la proxima revision pasa a hoy mas sus meses, y queda anotado")
    void recertificar() {
        proceso.setDuenoId(ANA);
        proceso.setMesesEntreRevisiones(6);
        proceso.setProximaRevision(HOY.minusDays(1));

        ProcesoResponse respuesta = procesoService.recertificar(EMPRESA, PROCESO, ANA, "  Revisado con logística ");

        assertThat(proceso.getProximaRevision()).isEqualTo(LocalDate.of(2027, 5, 12));
        assertThat(respuesta.proximaRevision()).isEqualTo(LocalDate.of(2027, 5, 12));
        assertThat(respuesta.revisionVencida()).isFalse();
        verify(permisos, never()).exigir(anyLong(), anyLong(), any(), any());
        verify(historialCambioService).registrar(proceso, ana, "Revisión hecha: el proceso sigue vigente. Próxima "
                + "revisión: 2027-05-12.");
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("proximaRevision", "2027-05-12");
        detalle.put("revisionAnterior", "2026-11-11");
        detalle.put("vencida", true);
        detalle.put("nota", "Revisado con logística");
        verify(auditoria).registrar(EMPRESA, ANA, "PROCESO_RECERTIFICADO", "PROCESO", PROCESO, detalle);
    }

    @Test
    @DisplayName("una revision del mismo dia no estaba vencida; sin fecha ni nota, el detalle no las inventa")
    void recertificarSinFecha() {
        proceso.setDuenoId(ANA);
        proceso.setProximaRevision(HOY);
        procesoService.recertificar(EMPRESA, PROCESO, ANA, null);
        Map<String, Object> delDia = new LinkedHashMap<>();
        delDia.put("proximaRevision", "2027-11-12");
        delDia.put("revisionAnterior", "2026-11-12");
        delDia.put("vencida", false);
        verify(auditoria).registrar(EMPRESA, ANA, "PROCESO_RECERTIFICADO", "PROCESO", PROCESO, delDia);

        proceso.setProximaRevision(null);
        procesoService.recertificar(EMPRESA, PROCESO, ANA, "   ");
        verify(auditoria).registrar(EMPRESA, ANA, "PROCESO_RECERTIFICADO", "PROCESO", PROCESO,
                Map.of("proximaRevision", "2027-11-12"));
    }

    @Test
    @DisplayName("quien no es su dueno necesita administrar el proceso para confirmarlo")
    void recertificarSinSerDueno() {
        proceso.setDuenoId(BETO);
        procesoService.recertificar(EMPRESA, PROCESO, ANA, null);
        verify(permisos).exigir(EMPRESA, ANA, Accion.ADMINISTRAR, Recurso.proceso(PROCESO));

        proceso.setProximaRevision(HOY);
        doThrow(new SinPermisoException("No")).when(permisos).exigir(EMPRESA, ANA, Accion.ADMINISTRAR,
                Recurso.proceso(PROCESO));
        assertThatThrownBy(() -> procesoService.recertificar(EMPRESA, PROCESO, ANA, null))
                .isInstanceOf(SinPermisoException.class);
        assertThat(proceso.getProximaRevision()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("la ficha cambia los meses entre revisiones solo si los trae")
    void mesesEnLaFicha() {
        when(procesoRepository.saveAndFlush(proceso)).thenReturn(proceso);
        procesoService.editarFicha(EMPRESA, PROCESO, ANA, new DatosDeFicha(null, null, null, null, List.of(),
                proceso.getVersion(), 24));
        assertThat(proceso.getMesesEntreRevisiones()).isEqualTo(24);

        procesoService.editarFicha(EMPRESA, PROCESO, ANA, new DatosDeFicha(null, null, null, null, List.of(),
                proceso.getVersion()));
        assertThat(proceso.getMesesEntreRevisiones()).isEqualTo(24);
    }

    @Test
    @DisplayName("una revision vencida es una fecha que ya paso; la de hoy todavia no")
    void revisionVencida() {
        assertThat(proceso.revisionVencida(HOY)).isFalse();
        proceso.setProximaRevision(HOY);
        assertThat(proceso.revisionVencida(HOY)).isFalse();
        proceso.setProximaRevision(HOY.minusDays(1));
        assertThat(proceso.revisionVencida(HOY)).isTrue();
        assertThat(procesoMapper.toResponse(proceso, HOY).revisionVencida()).isTrue();
        assertThat(procesoMapper.toResponse(proceso, HOY.minusDays(1)).revisionVencida()).isFalse();
        assertThat(procesoMapper.toResponse(proceso, HOY).mesesEntreRevisiones()).isEqualTo(12);
        // El proceso de una instantanea no dice nada que cambie con el calendario
        assertThat(procesoMapper.toResponse(proceso).revisionVencida()).isNull();
    }
}
