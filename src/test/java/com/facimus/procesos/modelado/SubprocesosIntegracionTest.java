package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.bpmn.EsquemaBpmn;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeTemporizador;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.modelado.service.SubprocesoService;

/** PR 56: subprocesos y actividades de llamada contra la base de verdad. */
@SpringBootTest
@ActiveProfiles("test")
class SubprocesosIntegracionTest {

    private static int tiendas;

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private PoolService poolService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private EventoService eventoService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private SubprocesoService subprocesoService;
    @Autowired
    private DiagramaService diagramaService;
    @Autowired
    private ModeloBpmnService modeloBpmnService;
    @Autowired
    private ImportacionBpmnService importacionBpmnService;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long tienda;
    private Long ventas;
    private Long almacen;
    private Long inicio;
    private Long fin;
    private ActividadResponse subproceso;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@subprocesos.test";
        empresaId = empresaService.registrar("Tienda con subprocesos " + numero, "9478" + numero + "000-1", admin,
                "Sara Subproceso", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
        procesoId = nuevoProceso("Order fulfillment");
        tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        ventas = lane(tienda, "Sales");
        almacen = lane(tienda, "Warehouse");
        inicio = eventoService.crear(empresaId, adminId, ventas, "Start", TipoEvento.INICIO, 0, 0).id();
        subproceso = actividadService.crear(empresaId, adminId, ventas, "Inside", null, TipoActividad.SUBPROCESO, 180, 0);
        fin = eventoService.crear(empresaId, adminId, ventas, "End", TipoEvento.FIN, 700, 0).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, subproceso.id()));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(subproceso.id(), fin));
    }

    @Test
    @DisplayName("Un nodo entra a un subproceso, queda en su lane y puede volver a salir")
    void mover() {
        ActividadResponse tarea = tarea("Child", almacen);

        subprocesoService.mover(empresaId, adminId, tarea.id(), subproceso.id());
        ActividadResponse dentro = actividadService.obtener(empresaId, tarea.id());
        assertThat(dentro.padreId()).isEqualTo(subproceso.id());
        assertThat(dentro.laneId()).isEqualTo(ventas);
        assertThat(dentro.version()).isEqualTo(tarea.version() + 1);

        subprocesoService.mover(empresaId, adminId, tarea.id(), null);
        assertThat(actividadService.obtener(empresaId, tarea.id()).padreId()).isNull();
    }

    @Test
    @DisplayName("Solo entra a un subproceso expandido del mismo pool, sin flujos y sin meterse en si mismo")
    void mover_reglas() {
        ActividadResponse tarea = tarea("Child", ventas);
        Long otroPool = poolService.crear(empresaId, adminId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, false,
                Integracion.NINGUNA).id();
        Long laneDeOtro = lane(otroPool, "Dispatch");
        ActividadResponse deOtroPool = actividadService.crear(empresaId, adminId, laneDeOtro, "Foreign", null,
                TipoActividad.SUBPROCESO, 0, 0);
        ActividadResponse colapsado = actividadService.crear(empresaId, adminId, ventas, "Box", null,
                TipoActividad.SUBPROCESO_COLAPSADO, 0, 0);
        ActividadResponse exterior = actividadService.crear(empresaId, adminId, ventas, "Outer", null,
                TipoActividad.SUBPROCESO, 0, 0);
        ActividadResponse interior = actividadService.crear(empresaId, adminId, ventas, "Inner", null,
                TipoActividad.SUBPROCESO, 0, 0);
        subprocesoService.mover(empresaId, adminId, interior.id(), exterior.id());

        assertThatThrownBy(() -> subprocesoService.mover(empresaId, adminId, inicio, subproceso.id()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("flujos");
        assertThatThrownBy(() -> subprocesoService.mover(empresaId, adminId, tarea.id(), colapsado.id()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> subprocesoService.mover(empresaId, adminId, tarea.id(), deOtroPool.id()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otro pool");
        assertThatThrownBy(() -> subprocesoService.mover(empresaId, adminId, exterior.id(), interior.id()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("si mismo");
        assertThatThrownBy(() -> subprocesoService.mover(empresaId, adminId, exterior.id(), exterior.id()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Un flujo no cruza el borde de un subproceso, y dentro de el si se conecta")
    void flujos() {
        ActividadResponse dentro = meter("Child");
        ActividadResponse otro = meter("Other");

        assertThatThrownBy(() -> arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, dentro.id())))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("borde");
        assertThat(arcoService.crear(empresaId, adminId, DatosDeArco.entre(dentro.id(), otro.id())).id()).isNotNull();
    }

    @Test
    @DisplayName("Lo de dentro no se muda solo de lane, y se muda con su subproceso")
    void lanes() {
        ActividadResponse dentro = meter("Child");

        assertThatThrownBy(() -> actividadService.editar(empresaId, adminId, dentro.id(), "Child", null,
                TipoActividad.USUARIO, almacen, 0, 0, actividadService.obtener(empresaId, dentro.id()).version()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("subproceso");

        actividadService.editar(empresaId, adminId, subproceso.id(), "Inside", null, TipoActividad.SUBPROCESO, almacen, 180,
                0, actividadService.obtener(empresaId, subproceso.id()).version());

        assertThat(actividadService.obtener(empresaId, dentro.id()).laneId()).isEqualTo(almacen);
    }

    @Test
    @DisplayName("Borrar un subproceso se lleva su contenido y sus flujos")
    void borrar() {
        ActividadResponse uno = meter("One");
        ActividadResponse dos = meter("Two");
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(uno.id(), dos.id()));

        actividadService.eliminar(empresaId, adminId, subproceso.id());

        DiagramaResponse diagrama = diagramaService.obtener(empresaId, procesoId);
        assertThat(diagrama.actividades()).isEmpty();
        assertThat(diagrama.arcos()).isEmpty();
    }

    @Test
    @DisplayName("Una llamada corre otro proceso de la tienda, nunca el suyo ni uno ajeno")
    void llamarA_reglas() {
        ActividadResponse llamada = llamada("Call", ventas);
        Long otro = nuevoProceso("Other");
        ActividadResponse tarea = tarea("Plain", ventas);

        assertThat(subprocesoService.llamarA(empresaId, adminId, llamada.id(), otro, llamada.version())
                .procesoLlamadoId()).isEqualTo(otro);
        Long version = actividadService.obtener(empresaId, llamada.id()).version();
        assertThatThrownBy(() -> subprocesoService.llamarA(empresaId, adminId, llamada.id(), procesoId, version))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> subprocesoService.llamarA(empresaId, adminId, tarea.id(), otro, tarea.version()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> subprocesoService.llamarA(empresaId, adminId, llamada.id(), 999_999L, version))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Un subproceso no se publica como ejecutable, y como documental si")
    void publicar_subproceso() {
        assertThatThrownBy(() -> publicar(procesoId)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("E-19")));

        cambiarModo(procesoId, ModoProceso.DOCUMENTAL);
        publicar(procesoId);

        assertThat(procesoService.obtener(empresaId, procesoId, false).estado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    @DisplayName("Las llamadas circulares no se publican")
    void publicar_circular() {
        cambiarModo(procesoId, ModoProceso.DOCUMENTAL);
        Long otro = nuevoProceso("Other");
        Long poolDelOtro = poolService.listarPorProceso(empresaId, otro).getFirst().id();
        Long laneDelOtro = lane(poolDelOtro, "Other lane");
        cambiarModo(otro, ModoProceso.DOCUMENTAL);
        ActividadResponse ida = llamada("There", ventas);
        ActividadResponse vuelta = llamada("Back", laneDelOtro);
        subprocesoService.llamarA(empresaId, adminId, ida.id(), otro, ida.version());
        subprocesoService.llamarA(empresaId, adminId, vuelta.id(), procesoId, vuelta.version());

        assertThatThrownBy(() -> publicar(procesoId)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("E-21")));
    }

    @Test
    @DisplayName("No se publica una llamada a un proceso dado de baja")
    void publicar_llamadoDeBaja() {
        cambiarModo(procesoId, ModoProceso.DOCUMENTAL);
        Long otro = nuevoProceso("Other");
        ActividadResponse ida = llamada("There", ventas);
        subprocesoService.llamarA(empresaId, adminId, ida.id(), otro, ida.version());
        procesoService.eliminarLogico(empresaId, otro, adminId);

        assertThatThrownBy(() -> publicar(procesoId)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("E-20").contains("baja")));
    }

    @Test
    @DisplayName("Lo que exporta un proceso con subprocesos valida y vuelve sin cambiar nada")
    void exportarEImportar() {
        ActividadResponse uno = meter("One");
        ActividadResponse dos = meter("Two");
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(uno.id(), dos.id()));
        eventoService.crear(empresaId, adminId, ventas, "Late", TipoEvento.TEMPORIZADOR_BORDE, 0, 0,
                new DatosDeTemporizador(TipoTemporizador.DURACION, "PT1H", subproceso.id(), true));
        actividadService.crear(empresaId, adminId, ventas, "Box", null, TipoActividad.SUBPROCESO_COLAPSADO, 500, 100);
        ActividadResponse llamada = llamada("Call", ventas);
        subprocesoService.llamarA(empresaId, adminId, llamada.id(), nuevoProceso("Other"), llamada.version());
        cambiarModo(procesoId, ModoProceso.DOCUMENTAL);

        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();
        assertThat(EsquemaBpmn.problemas(exportado.getBytes(StandardCharsets.UTF_8))).isEmpty();

        ImportacionResponse vuelta = importacionBpmnService.reemplazar(empresaId, adminId, RolAcceso.ADMINISTRADOR,
                procesoId, entrada(exportado));

        assertThat(vuelta.creados() + vuelta.editados() + vuelta.dadosDeBaja()).isZero();
        assertThat(vuelta.informe().noSoportados()).isEmpty();
        assertThat(vuelta.informe().convertidos()).isEmpty();
        assertThat(modeloBpmnService.exportar(empresaId, procesoId).xml()).isEqualTo(exportado);

        ImportacionResponse copia = importacionBpmnService.importar(empresaId, adminId, RolAcceso.ADMINISTRADOR,
                entrada(exportado), "Copy");
        DiagramaResponse diagrama = diagramaService.obtener(empresaId, copia.procesoId());
        assertThat(diagrama.actividades()).filteredOn(actividad -> actividad.nombre().equals("One")).singleElement()
                .satisfies(actividad -> assertThat(actividad.padreId()).isNotNull());
        assertThat(diagrama.actividades()).filteredOn(actividad -> actividad.nombre().equals("Call")).singleElement()
                .satisfies(actividad -> assertThat(actividad.procesoLlamadoId()).isNotNull());
        assertThat(diagrama.eventos()).extracting(EventoResponse::adjuntoAId).filteredOn(java.util.Objects::nonNull)
                .hasSize(1);
    }

    private ActividadResponse meter(String nombre) {
        ActividadResponse tarea = tarea(nombre, ventas);
        subprocesoService.mover(empresaId, adminId, tarea.id(), subproceso.id());
        return actividadService.obtener(empresaId, tarea.id());
    }

    private ActividadResponse tarea(String nombre, Long lane) {
        return actividadService.crear(empresaId, adminId, lane, nombre, null, TipoActividad.USUARIO, 200, 40);
    }

    private ActividadResponse llamada(String nombre, Long lane) {
        return actividadService.crear(empresaId, adminId, lane, nombre, null, TipoActividad.LLAMADA, 400, 40);
    }

    private Long nuevoProceso(String nombre) {
        return procesoService.crear(empresaId, adminId, nombre, "Description", "Misc").id();
    }

    private Long lane(Long pool, String nombre) {
        return laneService.crear(empresaId, adminId, pool, nombre,
                rolProcesoService.crear(empresaId, adminId, nombre, null).id()).id();
    }

    private void cambiarModo(Long proceso, ModoProceso modo) {
        procesoService.cambiarModo(empresaId, proceso, adminId, modo,
                procesoService.obtener(empresaId, proceso, false).version());
    }

    private void publicar(Long proceso) {
        procesoService.cambiarEstado(empresaId, proceso, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, proceso, false).version());
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
