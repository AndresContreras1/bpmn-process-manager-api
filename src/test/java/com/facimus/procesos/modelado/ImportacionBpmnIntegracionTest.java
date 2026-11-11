package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * PR 54: importar es el camino de vuelta de exportar. Lo que un proceso exporta, importado en el mismo proceso, no
 * cambia nada; importado como proceso nuevo, da el mismo modelo; y un archivo que cambia algo cambia solo eso.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportacionBpmnIntegracionTest {

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
    private GatewayService gatewayService;

    @Autowired
    private ArcoService arcoService;

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private ModeloBpmnService modeloBpmnService;

    @Autowired
    private ImportacionBpmnService importacionBpmnService;

    @Autowired
    private DiagramaService diagramaService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@importar.test";
        empresaId = empresaService.registrar("Tienda que importa " + numero, "9062" + numero + "000-1", admin,
                "Ines Importa", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.CLIENTE).id();
        Long ventas = laneService.crear(empresaId, adminId, tienda, "Sales",
                rolProcesoService.crear(empresaId, adminId, "Sales", null).id()).id();
        Long almacen = laneService.crear(empresaId, adminId, tienda, "Warehouse",
                rolProcesoService.crear(empresaId, adminId, "Warehouse", null).id()).id();
        Long inicio = eventoService.crear(empresaId, adminId, ventas, "Order received", TipoEvento.MENSAJE_INICIO, 40,
                10).id();
        Long revisar = actividadService.crear(empresaId, adminId, ventas, "Review order", "Check stock & price",
                TipoActividad.USUARIO, 220, 10).id();
        Long aprobado = gatewayService.crear(empresaId, adminId, ventas, "Approved?", TipoGateway.EXCLUSIVO, 400, 10)
                .id();
        Long enviar = actividadService.crear(empresaId, adminId, almacen, "Ship order", null, TipoActividad.USUARIO,
                580, 30).id();
        Long enviado = eventoService.crear(empresaId, adminId, almacen, "Shipped", TipoEvento.FIN, 760, 30).id();
        Long rechazado = eventoService.crear(empresaId, adminId, ventas, "Rejected", TipoEvento.FIN, 580, 110).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, revisar));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(revisar, aprobado));
        arcoService.crear(empresaId, adminId, new DatosDeArco(aprobado, enviar, "Yes", "total > 100", false, 1));
        arcoService.crear(empresaId, adminId, new DatosDeArco(aprobado, rechazado, "No", null, true, 2));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(enviar, enviado));
        mensajeService.crear(empresaId, adminId, procesoId, new DatosDeMensaje("Order placed", "Cart and address",
                cliente, tienda, null, inicio, null, null, null, false, List.of(), null, "order", null));
    }

    @Test
    @DisplayName("Importar en el proceso lo que el mismo proceso exporta no cambia nada: ni una fila ni la huella")
    void loQueExporta_noCambiaNada() {
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();
        String versiones = versiones();
        int historial = historial();

        ImportacionResponse importacion = reemplazar(exportado, RolAcceso.ADMINISTRADOR);

        assertThat(importacion.creados() + importacion.editados() + importacion.dadosDeBaja()).isZero();
        assertThat(importacion.informe().convertidos()).isEmpty();
        assertThat(importacion.informe().noSoportados()).isEmpty();
        assertThat(importacion.informe().avisosDelEsquema()).isEmpty();
        assertThat(versiones()).isEqualTo(versiones);
        assertThat(historial()).isEqualTo(historial);
        assertThat(modeloBpmnService.exportar(empresaId, procesoId).xml()).isEqualTo(exportado);
    }

    @Test
    @DisplayName("Importado como proceso nuevo da el mismo modelo, y lo que ese exporta vuelve a dar lo mismo")
    void comoProcesoNuevo_mismoModelo() {
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();

        ImportacionResponse importacion = importar(exportado, "Order fulfillment (copy)", RolAcceso.ADMINISTRADOR);

        assertThat(importacion.informe().convertidos()).isEmpty();
        assertThat(importacion.informe().noSoportados()).isEmpty();
        assertThat(importacion.informe().soportados()).containsEntry("pools", 2).containsEntry("lanes", 2)
                .containsEntry("tareas", 2).containsEntry("gateways", 1).containsEntry("eventos", 3)
                .containsEntry("flujos", 5).containsEntry("mensajes", 1);
        assertThat(modelo(importacion.procesoId())).isEqualTo(modelo(procesoId));
        // Y la copia, exportada e importada sobre si misma, tampoco cambia
        String copia = modeloBpmnService.exportar(empresaId, importacion.procesoId()).xml();
        ImportacionResponse otraVez = importacionBpmnService.reemplazar(empresaId, adminId, RolAcceso.ADMINISTRADOR,
                importacion.procesoId(), entrada(copia));
        assertThat(otraVez.creados() + otraVez.editados() + otraVez.dadosDeBaja()).isZero();
    }

    @Test
    @DisplayName("Un archivo que renombra, agrega y quita cambia solo eso, en una linea del historial")
    void porDiferencias() {
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();
        String rechazado = idDe(exportado, "Rejected");
        String flujoAlRechazo = jdbc.queryForObject(
                "select a.bpmn_id from arcos a join nodos_flujo n on n.id = a.destino_id where n.bpmn_id = ? "
                        + "and a.activo", String.class, rechazado);
        int historial = historial();
        // Se renombra una tarea, el rechazo deja de existir (con su flujo) y la salida por defecto va a otro fin
        String cambiado = exportado
                .replace("name=\"Review order\"", "name=\"Review the order\"")
                .replace("targetRef=\"" + rechazado + "\"", "targetRef=\"Event_cancelado\"")
                .replace("<bpmn:endEvent id=\"" + rechazado + "\" name=\"Rejected\">",
                        "<bpmn:endEvent id=\"Event_cancelado\" name=\"Cancelled\">")
                .replace("<bpmn:flowNodeRef>" + rechazado + "</bpmn:flowNodeRef>",
                        "<bpmn:flowNodeRef>Event_cancelado</bpmn:flowNodeRef>")
                .replace("bpmnElement=\"" + rechazado + "\"", "bpmnElement=\"Event_cancelado\"");

        ImportacionResponse importacion = reemplazar(cambiado, RolAcceso.ADMINISTRADOR);

        assertThat(importacion.creados()).isEqualTo(1);
        assertThat(importacion.dadosDeBaja()).isEqualTo(1);
        assertThat(importacion.editados()).isEqualTo(2);
        DiagramaResponse diagrama = diagramaService.obtener(empresaId, procesoId);
        assertThat(diagrama.actividades()).extracting(actividad -> actividad.nombre())
                .containsExactlyInAnyOrder("Review the order", "Ship order");
        assertThat(diagrama.eventos()).extracting(evento -> evento.nombre())
                .containsExactlyInAnyOrder("Order received", "Shipped", "Cancelled");
        assertThat(diagrama.arcos()).filteredOn(arco -> flujoAlRechazo.equals(arco.bpmnId())).singleElement()
                .satisfies(arco -> assertThat(arco.porDefecto()).isTrue());
        assertThat(historial()).isEqualTo(historial + 1);
    }

    @Test
    @DisplayName("Un editor no da de baja con un archivo: eso es de un administrador, como los DELETE")
    void editor_noDaDeBaja() {
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();
        String sinElRechazo = sinNodo(exportado, "Rejected");
        String versiones = versiones();

        assertThatThrownBy(() -> reemplazar(sinElRechazo, RolAcceso.EDITOR))
                .isInstanceOf(SinPermisoException.class).hasMessageContaining("administrador");
        assertThat(versiones()).isEqualTo(versiones);
    }

    @Test
    @DisplayName("Lanes con roles que no existen: un editor no puede seguir, y el proceso nuevo no queda a medias")
    void rolesQueFaltan_editor() {
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml()
                .replace("rol=\"Warehouse\"", "rol=\"Night shift\"");
        int procesos = procesos();

        assertThatThrownBy(() -> importar(exportado, "Otra copia", RolAcceso.EDITOR))
                .isInstanceOf(SinPermisoException.class).hasMessageContaining("Night shift");
        assertThat(procesos()).isEqualTo(procesos);
        // Un administrador si, y el rol queda creado
        importar(exportado, "Otra copia", RolAcceso.ADMINISTRADOR);
        assertThat(jdbc.queryForObject("select count(*) from roles_proceso where empresa_id = ? and nombre = ?",
                Integer.class, empresaId, "Night shift")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un archivo que rompe las reglas del modelo no se importa, y la respuesta dice todo lo que falla")
    void reglasRotas() {
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();
        String inicio = idDe(exportado, "Order received");
        String fin = idDe(exportado, "Shipped");
        // Un flujo que sale de un fin y llega a un inicio: dos errores en uno
        String roto = exportado.replace("</bpmn:process>", "<bpmn:sequenceFlow id=\"Flow_roto\" sourceRef=\"" + fin
                + "\" targetRef=\"" + inicio + "\"/></bpmn:process>");
        String versiones = versiones();

        assertThatThrownBy(() -> reemplazar(roto, RolAcceso.ADMINISTRADOR))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("Flow_roto").contains("fin")));
        assertThat(versiones()).isEqualTo(versiones);
    }

    private ImportacionResponse reemplazar(String xml, RolAcceso rol) {
        return importacionBpmnService.reemplazar(empresaId, adminId, rol, procesoId, entrada(xml));
    }

    private ImportacionResponse importar(String xml, String nombre, RolAcceso rol) {
        return importacionBpmnService.importar(empresaId, adminId, rol, entrada(xml), nombre);
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** El id del nodo con ese nombre en el archivo. */
    private static String idDe(String xml, String nombre) {
        int nombreEn = xml.indexOf("name=\"" + nombre + "\"");
        int idEn = xml.lastIndexOf("id=\"", nombreEn);
        return xml.substring(idEn + 4, xml.indexOf('"', idEn + 4));
    }

    /** El archivo sin un fin y sin lo que apunta a el. */
    private static String sinNodo(String xml, String nombre) {
        String id = idDe(xml, nombre);
        return xml.replaceAll("(?s)<bpmn:endEvent id=\"" + id + "\".*?</bpmn:endEvent>", "")
                .replaceAll("<bpmn:sequenceFlow [^>]*targetRef=\"" + id + "\"[^>]*/>", "")
                .replaceAll("<bpmn:flowNodeRef>" + id + "</bpmn:flowNodeRef>", "")
                .replaceAll("(?s)<bpmndi:BPMNShape id=\"" + id + "_di\".*?</bpmndi:BPMNShape>", "");
    }

    /** Lo que es el modelo, sin ids de la base: lo que dos procesos con el mismo dibujo tienen igual. */
    private List<String> modelo(Long proceso) {
        return jdbc.queryForList("""
                select 'nodo ' || n.tipo_nodo || ' ' || n.nombre || ' ' || coalesce(n.tipo_actividad, '')
                       || coalesce(n.tipo_gateway, '') || coalesce(n.tipo_evento, '') || ' ' || l.nombre || ' '
                       || (n.posicion_x - min(n.posicion_x) over ()) || ','
                       || (n.posicion_y - min(n.posicion_y) over (partition by n.lane_id)) || ' '
                       || coalesce(n.descripcion, '')
                from nodos_flujo n join lanes l on l.id = n.lane_id join pools p on p.id = l.pool_id
                where p.proceso_id = ? and n.activo
                union all
                select 'arco ' || o.nombre || '>' || d.nombre || ' ' || coalesce(a.etiqueta, '') || ' '
                       || coalesce(a.condicion, '') || ' ' || a.por_defecto || ' ' || a.orden
                from arcos a join nodos_flujo o on o.id = a.origen_id join nodos_flujo d on d.id = a.destino_id
                join pools p on p.id = a.pool_id where p.proceso_id = ? and a.activo
                union all
                select 'lane ' || l.nombre || ' ' || l.orden || ' ' || r.nombre
                from lanes l join roles_proceso r on r.id = l.rol_proceso_id join pools p on p.id = l.pool_id
                where p.proceso_id = ? and l.activo
                union all
                select 'pool ' || p.tipo_participante || ' ' || p.caja_negra || ' ' || p.integracion || ' ' || p.orden
                       || ' ' || (case when p.tipo_participante = 'EMPRESA' then '' else p.nombre end)
                from pools p where p.proceso_id = ? and p.activo
                union all
                select 'mensaje ' || m.nombre || ' ' || m.contenido || ' ' || coalesce(m.variable, '')
                from mensajes m where m.proceso_id = ? and m.activo
                order by 1
                """, String.class, proceso, proceso, proceso, proceso, proceso);
    }

    /** La version optimista de cada elemento del proceso: si no cambia, nadie escribio. */
    private String versiones() {
        return String.join(",", jdbc.queryForList("""
                select 'p' || id || ':' || version from pools where proceso_id = ?
                union all select 'l' || l.id || ':' || l.version from lanes l join pools p on p.id = l.pool_id
                    where p.proceso_id = ?
                union all select 'n' || n.id || ':' || n.version || n.activo from nodos_flujo n
                    join lanes l on l.id = n.lane_id join pools p on p.id = l.pool_id where p.proceso_id = ?
                union all select 'a' || a.id || ':' || a.version || a.activo from arcos a join pools p
                    on p.id = a.pool_id where p.proceso_id = ?
                union all select 'm' || id || ':' || version || activo from mensajes where proceso_id = ?
                order by 1
                """, String.class, procesoId, procesoId, procesoId, procesoId, procesoId));
    }

    private int historial() {
        return jdbc.queryForObject("select count(*) from historial_cambios where proceso_id = ?", Integer.class,
                procesoId);
    }

    private int procesos() {
        return jdbc.queryForObject("select count(*) from procesos where empresa_id = ?", Integer.class, empresaId);
    }
}
