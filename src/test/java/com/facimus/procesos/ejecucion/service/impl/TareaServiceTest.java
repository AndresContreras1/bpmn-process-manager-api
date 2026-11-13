package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.formularios.ArchivosDeFormularios;
import com.facimus.procesos.common.formularios.CampoArmado;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.dto.response.FormularioDeTareaResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.CampoDeFormularioResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

/**
 * Las reglas de la bandeja: que es una tarea, quien la completa y cuantas veces. Que el caso siga despues es cosa
 * del motor, que tiene su propia prueba.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TareaServiceTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long EDITOR = 3L;
    private static final Long TAREA_ID = 77L;
    private static final Long CASO_ID = 42L;
    /** El tick en que la tienda esta cuando se completa la tarea. */
    private static final int TICK = 4;

    @Mock
    private ActividadCasoRepository actividadCasoRepository;

    @Mock
    private CasoRepository casoRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private MembresiaRolService membresiaRolService;

    @Mock
    private GrafosDeVersion grafos;

    @Mock
    private MotorDeProcesos motor;

    @Mock
    private RelojDeLaTienda reloj;

    @Mock
    private Bitacora bitacora;

    @Mock
    private CasoMapper casoMapper;

    @Mock
    private ArchivosDeFormularios archivos;

    private final JsonMapper json = JsonMapper.builder().build();

    @InjectMocks
    private TareaServiceImpl tareaService;

    private Empresa tienda;
    private VersionProceso version;
    private Caso caso;

    @BeforeEach
    void laTiendaYSuCaso() {
        tareaService = new TareaServiceImpl(actividadCasoRepository, casoRepository, usuarioService,
                membresiaRolService, grafos, motor, reloj, bitacora, casoMapper, json,
                new FormulariosDelCaso(usuarioService, archivos));
        given(reloj.ahora(TIENDA_ID)).willReturn(TICK);
        tienda = Empresa.builder().id(TIENDA_ID).nombre("Demo Store").build();
        Proceso proceso = Proceso.builder().id(10L).empresa(tienda).nombre("Order fulfillment")
                .estado(EstadoProceso.PUBLICADO).activo(true).build();
        version = VersionProceso.builder().id(5L).empresa(tienda).proceso(proceso).numero(1)
                .estado(EstadoVersion.VIGENTE).definicion("{}").build();
        caso = Caso.builder().id(CASO_ID).empresa(tienda).proceso(proceso).versionProceso(version)
                .referencia("ORD-1").estado(EstadoCaso.ABIERTO).variables("{}").build();
        given(casoMapper.aJson(any())).willAnswer(llamada -> json.writeValueAsString(llamada.getArgument(0)));
        given(actividadCasoRepository.save(any(ActividadCaso.class))).willAnswer(l -> l.getArgument(0));
        given(actividadCasoRepository.casoDe(TAREA_ID, TIENDA_ID)).willReturn(Optional.of(CASO_ID));
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso));
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(unProcesoConTarea().diagrama()));
    }

    @Test
    @DisplayName("R-49: una tarea ya completada no se completa otra vez")
    void completar_tareaYaCompletada_esUnaReglaDeNegocio() {
        conTarea(EstadoActividadCaso.COMPLETADA);

        assertThatThrownBy(() -> tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La tarea ya fue completada.");
        verify(motor, never()).seguirDesde(any(), any(), any(), any());
    }

    @Test
    @DisplayName("La tarea de un caso ya cerrado tampoco se completa")
    void completar_casoCerrado_esUnaReglaDeNegocio() {
        caso.setEstado(EstadoCaso.CANCELADO);
        conTarea(EstadoActividadCaso.EN_ESPERA);

        assertThatThrownBy(() -> tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El caso ya está cerrado.");
    }

    @Test
    @DisplayName("D3: el caso se bloquea antes de leer la tarea, no despues")
    void completar_bloqueaElCasoAntesDeLeerLaTarea() {
        conTarea(EstadoActividadCaso.EN_ESPERA);

        tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of());

        InOrder enOrden = inOrder(casoRepository, actividadCasoRepository);
        enOrden.verify(casoRepository).bloquear(CASO_ID, TIENDA_ID);
        enOrden.verify(actividadCasoRepository).findByIdAndEmpresaId(TAREA_ID, TIENDA_ID);
    }

    @Test
    @DisplayName("Los datos de la tarea entran a las variables del caso bajo su nombre en camello")
    void completar_guardaLosDatosEnLasVariables() {
        conTarea(EstadoActividadCaso.EN_ESPERA);

        tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of("packedItems", 3));

        assertThat(caso.getVariables()).isEqualTo("{\"tarea\":{\"pickAndPackItems\":{\"packedItems\":3}}}");
    }

    @Test
    @DisplayName("Completar deja la tarea completada y le dice al motor que siga desde su nodo")
    void completar_completaYSigue() {
        ActividadCaso tarea = conTarea(EstadoActividadCaso.EN_ESPERA);

        tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, null);

        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
        assertThat(tarea.getDatosSalida()).isNull();
        verify(motor).seguirDesde(eq(caso), any(GrafoDeVersion.class), eq(tarea.getNodoId()),
                eq(new Momento(TICK, EDITOR)));
    }

    @Test
    @DisplayName("La tarea de otra tienda no existe para esta")
    void completar_deOtraTienda_noExiste() {
        given(actividadCasoRepository.casoDe(TAREA_ID, TIENDA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Tarea no encontrada.");
    }

    @Test
    @DisplayName("Un paso del caso que no es una actividad de usuario no es una tarea")
    void obtener_pasoQueNoEsTarea_noExiste() {
        given(actividadCasoRepository.findByIdAndEmpresaId(TAREA_ID, TIENDA_ID))
                .willReturn(Optional.of(paso(TipoNodoCaso.GATEWAY, "EXCLUSIVO", EstadoActividadCaso.EN_ESPERA)));

        assertThatThrownBy(() -> tareaService.obtener(TIENDA_ID, TAREA_ID))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Tarea no encontrada.");
    }

    @Test
    @DisplayName("Una actividad de servicio tampoco es una tarea de nadie")
    void obtener_actividadDeServicio_noEsTarea() {
        given(actividadCasoRepository.findByIdAndEmpresaId(TAREA_ID, TIENDA_ID))
                .willReturn(Optional.of(paso(TipoNodoCaso.ACTIVIDAD, "SERVICIO", EstadoActividadCaso.EN_ESPERA)));

        assertThatThrownBy(() -> tareaService.obtener(TIENDA_ID, TAREA_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Reservar una tarea comprueba que el usuario es de la tienda")
    void asignar_compruebaElUsuario() {
        ActividadCaso tarea = conTarea(EstadoActividadCaso.EN_ESPERA);

        tareaService.asignar(TIENDA_ID, TAREA_ID, EDITOR);

        verify(usuarioService).obtener(TIENDA_ID, EDITOR);
        assertThat(tarea.getAsignadoA()).isEqualTo(EDITOR);
    }

    @Test
    @DisplayName("Sin usuario, la tarea vuelve a quedar libre y no se pregunta por nadie")
    void asignar_sinUsuario_liberaLaTarea() {
        ActividadCaso tarea = conTarea(EstadoActividadCaso.EN_ESPERA);
        tarea.setAsignadoA(EDITOR);

        tareaService.asignar(TIENDA_ID, TAREA_ID, null);

        assertThat(tarea.getAsignadoA()).isNull();
        verify(usuarioService, never()).obtener(any(), any());
    }

    @Test
    @DisplayName("Una tarea ya completada no se reserva")
    void asignar_tareaCompletada_esUnaReglaDeNegocio() {
        conTarea(EstadoActividadCaso.COMPLETADA);

        assertThatThrownBy(() -> tareaService.asignar(TIENDA_ID, TAREA_ID, EDITOR))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La tarea ya fue completada.");
    }

    @Test
    @DisplayName("La bandeja sin estado trae las que esperan a alguien")
    void bandeja_porDefectoTraeLasQueEsperan() {
        given(actividadCasoRepository.bandejaPorRol(eq(TIENDA_ID), any(), any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        tareaService.bandeja(TIENDA_ID, EDITOR, false, null, null, null, Paginacion.de(0, 10));

        verify(actividadCasoRepository).bandejaPorRol(TIENDA_ID, null, null, EstadoActividadCaso.EN_ESPERA,
                Paginacion.de(0, 10));
    }

    @Test
    @DisplayName("D13: la bandeja propia pregunta solo por los roles de quien la pide")
    void bandejaPropia_consultaSusRoles() {
        given(membresiaRolService.idsDeLosRolesDe(TIENDA_ID, EDITOR)).willReturn(java.util.List.of(2L, 3L));
        given(actividadCasoRepository.bandejaDeMisRoles(eq(TIENDA_ID), any(), any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        tareaService.bandeja(TIENDA_ID, EDITOR, true, null, null, null, Paginacion.de(0, 10));

        verify(actividadCasoRepository).bandejaDeMisRoles(TIENDA_ID, java.util.List.of(2L, 3L), null,
                EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10));
        verify(actividadCasoRepository, never()).bandejaPorRol(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Quien no tiene ningun rol no tiene bandeja propia, y no se consulta nada")
    void bandejaPropia_sinRoles_vaciaSinConsultar() {
        given(membresiaRolService.idsDeLosRolesDe(TIENDA_ID, EDITOR)).willReturn(java.util.List.of());

        var pagina = tareaService.bandeja(TIENDA_ID, EDITOR, true, null, null, null, Paginacion.de(0, 10));

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalElements()).isZero();
        verify(actividadCasoRepository, never()).bandejaDeMisRoles(any(), any(), any(), any(), any());
        verify(actividadCasoRepository, never()).bandejaPorRol(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Pedir la bandeja propia de un rol que no es suyo no devuelve las tareas de ese rol")
    void bandejaPropia_conRolAjeno_vacia() {
        given(membresiaRolService.idsDeLosRolesDe(TIENDA_ID, EDITOR)).willReturn(java.util.List.of(2L));

        var pagina = tareaService.bandeja(TIENDA_ID, EDITOR, true, 9L, null, null, Paginacion.de(0, 10));

        assertThat(pagina.content()).isEmpty();
        verify(actividadCasoRepository, never()).bandejaDeMisRoles(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Pedir la bandeja propia de uno de sus roles la acota a ese")
    void bandejaPropia_conRolPropio_loAcota() {
        given(membresiaRolService.idsDeLosRolesDe(TIENDA_ID, EDITOR)).willReturn(java.util.List.of(2L, 3L));
        given(actividadCasoRepository.bandejaDeMisRoles(eq(TIENDA_ID), any(), any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        tareaService.bandeja(TIENDA_ID, EDITOR, true, 3L, null, null, Paginacion.de(0, 10));

        verify(actividadCasoRepository).bandejaDeMisRoles(TIENDA_ID, java.util.List.of(3L), null,
                EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10));
    }

    // ---------- PR 69: el formulario de la tarea ----------

    /** La tarea del proceso minimo, con un formulario congelado en su version. */
    private ActividadCaso conFormulario(EstadoActividadCaso estado) {
        DiagramaArmado armado = unProcesoConTarea();
        armado.formulario("Pick and pack items", CampoArmado.formulario(
                CampoArmado.campo("cajas", TipoDeCampo.NUMERO).requerido().minimo("1").porDefecto("order.boxes"),
                CampoArmado.campo("fragil", TipoDeCampo.SI_NO),
                CampoArmado.campo("motivo", TipoDeCampo.TEXTO).visibleSi("fragil == true").requerido()));
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(armado.diagrama()));
        ActividadCaso tarea = conTarea(estado);
        tarea.setNodoId(armado.id("Pick and pack items"));
        return tarea;
    }

    @Test
    @DisplayName("PR 69: con formulario, las respuestas entran con su tipo bajo tarea.<nombre> y en los datos de salida")
    void completar_conFormulario_guardaLosValoresConTipo() {
        ActividadCaso tarea = conFormulario(EstadoActividadCaso.EN_ESPERA);

        tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of("cajas", 2.0, "fragil", false, "motivo", "x"));

        assertThat(caso.getVariables())
                .isEqualTo("{\"tarea\":{\"pickAndPackItems\":{\"cajas\":2,\"fragil\":false}}}");
        assertThat(tarea.getDatosSalida()).isEqualTo("{\"cajas\":2,\"fragil\":false}");
        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
        verify(motor).seguirDesde(eq(caso), any(GrafoDeVersion.class), eq(tarea.getNodoId()), any());
    }

    @Test
    @DisplayName("PR 69: una respuesta que no cumple el formulario es un 400 con cada campo, y la tarea no se completa")
    void completar_conFormulario_respuestasMalas() {
        ActividadCaso tarea = conFormulario(EstadoActividadCaso.EN_ESPERA);
        Map<String, Object> malas = Map.of("cajas", 0, "fragil", true, "extra", 1);

        assertThatThrownBy(() -> tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, malas))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                        .containsExactly(Map.entry("datos.cajas", "Debe ser al menos 1."),
                                Map.entry("datos.extra", "No es un campo de este formulario."),
                                Map.entry("datos.motivo", "Es obligatorio.")));
        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
        assertThat(caso.getVariables()).isEqualTo("{}");
        verify(actividadCasoRepository, never()).save(any());
        verify(motor, never()).seguirDesde(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PR 69: con formulario, tampoco en PRUEBA entran datos libres")
    void completar_conFormulario_enPruebaTambienValida() {
        conFormulario(EstadoActividadCaso.EN_ESPERA);
        Map<String, Object> libres = Map.of("packedItems", 3);

        assertThatThrownBy(() -> tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, libres))
                .isInstanceOf(CamposInvalidosException.class);
    }

    @Test
    @DisplayName("PR 69: en produccion, una tarea sin formulario no recibe datos libres")
    void completar_enProduccionSinFormulario_noRecibeDatos() {
        caso.setModo(ModoCaso.PRODUCCION);
        ActividadCaso tarea = conTarea(EstadoActividadCaso.EN_ESPERA);
        Map<String, Object> libres = Map.of("packedItems", 3);

        assertThatThrownBy(() -> tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, libres))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                    assertThat(error).hasMessage(FormulariosDelCaso.SIN_FORMULARIO);
                    assertThat(error.errores()).containsExactly(Map.entry("datos.packedItems",
                            FormulariosDelCaso.NO_ES_UN_CAMPO));
                });
        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
    }

    @Test
    @DisplayName("PR 69: en produccion, una tarea sin formulario se completa sin datos")
    void completar_enProduccionSinDatos() {
        caso.setModo(ModoCaso.PRODUCCION);
        ActividadCaso tarea = conTarea(EstadoActividadCaso.EN_ESPERA);

        tareaService.completar(TIENDA_ID, EDITOR, TAREA_ID, Map.of());

        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
        assertThat(tarea.getDatosSalida()).isNull();
        assertThat(caso.getVariables()).isEqualTo("{}");
    }

    @Test
    @DisplayName("PR 69: el formulario de la tarea trae lo que propone cada campo y las variables que leen sus condiciones")
    void formulario_conLoQueProponeYLasVariables() {
        caso.setVariables("{\"order\":{\"boxes\":4}}");
        conFormulario(EstadoActividadCaso.EN_ESPERA);

        FormularioDeTareaResponse respuesta = tareaService.formulario(TIENDA_ID, TAREA_ID);

        assertThat(respuesta.tareaId()).isEqualTo(TAREA_ID);
        assertThat(respuesta.casoId()).isEqualTo(CASO_ID);
        assertThat(respuesta.nodo()).isEqualTo("Pick and pack items");
        assertThat(respuesta.formulario().campos()).extracting(CampoDeFormularioResponse::nombre)
                .containsExactly("cajas", "fragil", "motivo");
        assertThat(respuesta.formulario().campos().get(2).visibleSiArbol().operador()).isEqualTo("==");
        assertThat(respuesta.valores()).containsExactly(Map.entry("cajas", 4L));
        assertThat(respuesta.variables()).containsEntry("order", Map.of("boxes", 4))
                .containsKey("caso");
        assertThat(respuesta.variables().get("caso")).isEqualTo(Map.of("referencia", "ORD-1", "tick", 0));
        verify(casoRepository, never()).bloquear(any(), any());
    }

    @Test
    @DisplayName("PR 69: una tarea sin formulario lo dice con uno vacio")
    void formulario_sinFormulario() {
        conTarea(EstadoActividadCaso.EN_ESPERA);

        FormularioDeTareaResponse respuesta = tareaService.formulario(TIENDA_ID, TAREA_ID);

        assertThat(respuesta.formulario()).isNull();
        assertThat(respuesta.valores()).isEmpty();
    }

    @Test
    @DisplayName("PR 69: el formulario de un paso que no es tarea, o de otra tienda, no existe")
    void formulario_soloDeTareas() {
        given(actividadCasoRepository.findByIdAndEmpresaId(TAREA_ID, TIENDA_ID))
                .willReturn(Optional.of(paso(TipoNodoCaso.GATEWAY, "EXCLUSIVO", EstadoActividadCaso.EN_ESPERA)));

        assertThatThrownBy(() -> tareaService.formulario(TIENDA_ID, TAREA_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> tareaService.formulario(2L, TAREA_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private ActividadCaso conTarea(EstadoActividadCaso estado) {
        ActividadCaso tarea = paso(TipoNodoCaso.ACTIVIDAD, TipoActividad.USUARIO.name(), estado);
        given(actividadCasoRepository.findByIdAndEmpresaId(TAREA_ID, TIENDA_ID)).willReturn(Optional.of(tarea));
        return tarea;
    }

    private ActividadCaso paso(TipoNodoCaso tipo, String subtipo, EstadoActividadCaso estado) {
        return ActividadCaso.builder().id(TAREA_ID).empresa(tienda).caso(caso).nodoId(3L)
                .nodoNombre("Pick and pack items").tipoNodo(tipo).subtipo(subtipo).estado(estado).build();
    }

    /** Un proceso minimo cuya unica tarea es la que se completa en estas pruebas. */
    private static DiagramaArmado unProcesoConTarea() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 1L);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Pick and pack items", TipoActividad.USUARIO);
        armado.arco("Start", "Pick and pack items");
        return armado;
    }
}
