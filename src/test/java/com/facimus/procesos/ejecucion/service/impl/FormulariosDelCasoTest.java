package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.common.formularios.CampoArmado.campo;
import static com.facimus.procesos.common.formularios.CampoArmado.formulario;
import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.common.formularios.ArchivosDeFormularios;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * PR 69: lo que entra a un caso por el inicio o por una tarea pasa por el formulario congelado; sin el, solo en PRUEBA
 * entran datos libres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormulariosDelCasoTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long PROCESO_ID = 10L;

    @Mock
    private UsuarioService usuarioService;
    @Mock
    private ArchivosDeFormularios archivos;

    private FormulariosDelCaso formularios;
    private GrafoDeVersion grafo;
    private GrafoDeVersion sinFormularios;
    private NodoDeLaVersion inicio;
    private Long tarea;

    @BeforeEach
    void unProcesoConFormularios() {
        formularios = new FormulariosDelCaso(usuarioService, archivos);
        sinFormularios = GrafoDeVersion.de(proceso().diagrama());
        DiagramaArmado armado = proceso();
        armado.formulario("Refund requested", formulario(campo("monto", TipoDeCampo.MONEDA).requerido(),
                campo("soporte", TipoDeCampo.ARCHIVO)));
        armado.formulario("Approve", formulario(campo("aprobado", TipoDeCampo.SI_NO).requerido(),
                campo("responsable", TipoDeCampo.PERSONA).requeridoSi("aprobado == false")));
        grafo = GrafoDeVersion.de(armado.diagrama());
        inicio = grafo.inicioAMano().orElseThrow();
        tarea = armado.id("Approve");
        given(usuarioService.obtener(TIENDA_ID, 5L)).willReturn(usuario(5L, true));
        given(usuarioService.obtener(TIENDA_ID, 6L)).willReturn(usuario(6L, false));
        given(usuarioService.obtener(TIENDA_ID, 7L)).willThrow(new RecursoNoEncontradoException("Usuario no "
                + "encontrado."));
        given(archivos.disponible(TIENDA_ID, PROCESO_ID, 40L)).willReturn(true);
    }

    @Test
    @DisplayName("Al abrir, las respuestas del inicio salen con su tipo")
    void alAbrir_conFormulario() {
        Map<String, Object> variables = formularios.alAbrir(TIENDA_ID, PROCESO_ID, grafo, inicio, ModoCaso.PRODUCCION,
                Map.of("monto", 150.5, "soporte", 40));

        assertThat(variables).containsExactly(Map.entry("monto", new BigDecimal("150.5")),
                Map.entry("soporte", 40L));
        verify(archivos).disponible(TIENDA_ID, PROCESO_ID, 40L);
    }

    @Test
    @DisplayName("Al abrir, lo que no cumple el formulario es un 400 con cada campo bajo variables")
    void alAbrir_respuestasMalas() {
        Map<String, Object> malas = Map.of("soporte", 41, "otro", "x");

        assertThatThrownBy(() -> formularios.alAbrir(TIENDA_ID, PROCESO_ID, grafo, inicio, ModoCaso.PRUEBA, malas))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                    assertThat(error).hasMessage(FormulariosDelCaso.NO_CUMPLE);
                    assertThat(error.errores()).containsExactly(
                            Map.entry("variables.monto", "Es obligatorio."),
                            Map.entry("variables.otro", "No es un campo de este formulario."),
                            Map.entry("variables.soporte", "No es un documento del proceso listo para usar."));
                });
    }

    @Test
    @DisplayName("Al completar, una persona activa de la tienda sirve; una inactiva o de otra tienda, no")
    void alCompletar_personas() {
        assertThat(completar(Map.of("aprobado", false, "responsable", 5)))
                .containsExactly(Map.entry("aprobado", false), Map.entry("responsable", 5L));
        assertThatThrownBy(() -> completar(Map.of("aprobado", false, "responsable", 6)))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                        .containsExactly(Map.entry("datos.responsable",
                                "No es una persona activa de la organizacion.")));
        assertThatThrownBy(() -> completar(Map.of("aprobado", false, "responsable", 7)))
                .isInstanceOf(CamposInvalidosException.class);
        assertThatThrownBy(() -> completar(Map.of("aprobado", false)))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                        .containsOnlyKeys("datos.responsable"));
    }

    @Test
    @DisplayName("Las condiciones de la tarea leen las variables del caso")
    void alCompletar_leeElCaso() {
        DiagramaArmado armado = proceso();
        armado.formulario("Approve", formulario(campo("motivo", TipoDeCampo.TEXTO).requeridoSi("order.total > 100")));
        GrafoDeVersion conCondicion = GrafoDeVersion.de(armado.diagrama());
        Variables caro = Variables.de(Map.of("order", Map.of("total", 500)));
        Map<String, Object> nada = Map.of();

        assertThatThrownBy(() -> formularios.alCompletar(TIENDA_ID, PROCESO_ID, conCondicion, tarea,
                ModoCaso.PRODUCCION, nada, caro)).isInstanceOf(CamposInvalidosException.class);
        assertThat(formularios.alCompletar(TIENDA_ID, PROCESO_ID, conCondicion, tarea, ModoCaso.PRODUCCION, nada,
                Variables.de(Map.of("order", Map.of("total", 50))))).isEmpty();
    }

    @Test
    @DisplayName("Sin formulario, en PRUEBA entran los datos libres tal cual; sin datos, nada")
    void sinFormulario_enPrueba() {
        Map<String, Object> libres = new HashMap<>(Map.of("packedItems", 3));

        assertThat(formularios.alCompletar(TIENDA_ID, PROCESO_ID, sinFormularios, tarea, ModoCaso.PRUEBA, libres,
                Variables.de(Map.of()))).isSameAs(libres);
        assertThat(formularios.alAbrir(TIENDA_ID, PROCESO_ID, sinFormularios,
                sinFormularios.inicioAMano().orElseThrow(), ModoCaso.PRUEBA, null)).isEmpty();
        verifyNoInteractions(usuarioService, archivos);
    }

    @Test
    @DisplayName("Sin formulario, en PRODUCCION no entran datos libres; sin datos se abre o se completa igual")
    void sinFormulario_enProduccion() {
        NodoDeLaVersion suInicio = sinFormularios.inicioAMano().orElseThrow();
        Map<String, Object> libres = Map.of("order", Map.of("total", 5), "canal", "web");

        assertThatThrownBy(() -> formularios.alAbrir(TIENDA_ID, PROCESO_ID, sinFormularios, suInicio,
                ModoCaso.PRODUCCION, libres))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                    assertThat(error).hasMessage(FormulariosDelCaso.SIN_FORMULARIO);
                    assertThat(error.errores()).containsOnlyKeys("variables.order", "variables.canal")
                            .containsValue(FormulariosDelCaso.NO_ES_UN_CAMPO);
                });
        assertThat(formularios.alAbrir(TIENDA_ID, PROCESO_ID, sinFormularios, suInicio, ModoCaso.PRODUCCION,
                Map.of())).isEmpty();
        assertThat(formularios.alCompletar(TIENDA_ID, PROCESO_ID, sinFormularios, tarea, ModoCaso.PRODUCCION, null,
                Variables.de(Map.of()))).isEmpty();
    }

    @Test
    @DisplayName("Lo que se propone sale del caso; sin formulario, nada")
    void iniciales() {
        DiagramaArmado armado = proceso();
        armado.formulario("Approve", formulario(campo("monto", TipoDeCampo.NUMERO).porDefecto("order.total")));
        GrafoDeVersion conDefecto = GrafoDeVersion.de(armado.diagrama());

        assertThat(FormulariosDelCaso.iniciales(conDefecto.formularioDe(tarea).orElseThrow(),
                Variables.de(Map.of("order", Map.of("total", 7))))).containsExactly(Map.entry("monto", 7L));
        assertThat(FormulariosDelCaso.iniciales(null, Variables.de(Map.of()))).isEmpty();
    }

    private Map<String, Object> completar(Map<String, Object> datos) {
        return formularios.alCompletar(TIENDA_ID, PROCESO_ID, grafo, tarea, ModoCaso.PRODUCCION, datos,
                Variables.de(Map.of()));
    }

    private static UsuarioResponse usuario(Long id, boolean activo) {
        return new UsuarioResponse(id, "Ana", "ana@demo.test", RolAcceso.EDITOR, activo, TIENDA_ID, 0L, null, null,
                null, null, false, null);
    }

    /** Un inicio a mano, una tarea de aprobacion y un fin. */
    private static DiagramaArmado proceso() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 1L);
        armado.evento(VENTAS, "Refund requested", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Approve", TipoActividad.USUARIO);
        armado.evento(VENTAS, "Done", TipoEvento.FIN);
        armado.arco("Refund requested", "Approve");
        armado.arco("Approve", "Done");
        return armado;
    }
}
