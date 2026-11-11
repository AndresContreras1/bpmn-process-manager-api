package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ModeloBpmn;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.bpmn.EspaciosBpmn;
import com.facimus.procesos.modelado.bpmn.EsquemaBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Forma;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.GeometriaService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;
import com.facimus.procesos.modelado.service.PoolService;

import db.migration.common.V44__bpmn_de_las_versiones;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PR 53: un proceso sale en BPMN 2.0.2 valido, con la geometria que el modelo guarda; cada version guarda el suyo al
 * publicarse, y la migracion les escribe a las versiones viejas exactamente lo mismo que habrian guardado.
 */
@SpringBootTest
@ActiveProfiles("test")
class ModeloBpmnIntegracionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
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
    private GeometriaService geometriaService;

    @Autowired
    private ModeloBpmnService modeloBpmnService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@bpmn.test";
        empresaId = empresaService.registrar("Tienda del BPMN " + numero, "9061" + numero + "000-1", admin,
                "Bea Bpmn", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.NINGUNA).id();
        Long ventas = laneService.crear(empresaId, adminId, tienda, "Sales",
                rolProcesoService.crear(empresaId, adminId, "Sales " + numero, null).id()).id();
        Long inicio = eventoService.crear(empresaId, adminId, ventas, "Order received", TipoEvento.INICIO, 0, 0).id();
        Long revisar = actividadService.crear(empresaId, adminId, ventas, "Review order", "Check stock & price",
                TipoActividad.USUARIO, 180, 0).id();
        Long aprobado = gatewayService.crear(empresaId, adminId, ventas, "Approved?", TipoGateway.EXCLUSIVO, 360, 0)
                .id();
        Long enviar = actividadService.crear(empresaId, adminId, ventas, "Ship order", null, TipoActividad.USUARIO,
                540, 0).id();
        Long enviado = eventoService.crear(empresaId, adminId, ventas, "Shipped", TipoEvento.FIN, 720, 0).id();
        Long rechazado = eventoService.crear(empresaId, adminId, ventas, "Rejected", TipoEvento.FIN, 540, 120).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, revisar));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(revisar, aprobado));
        arcoService.crear(empresaId, adminId, new DatosDeArco(aprobado, enviar, "Yes", "total > 100", false, 1));
        arcoService.crear(empresaId, adminId, new DatosDeArco(aprobado, rechazado, "No", null, true, 2));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(enviar, enviado));
        mensajeService.crear(empresaId, adminId, procesoId, DatosDeMensaje.basico("Order placed", "Cart", cliente,
                tienda));
    }

    @Test
    @DisplayName("El borrador exportado valida contra la OMG y lleva la geometria que el modelo guarda")
    void elBorrador_valida() {
        ModeloBpmn modelo = modeloBpmnService.exportar(empresaId, procesoId);
        byte[] xml = modelo.xml().getBytes(StandardCharsets.UTF_8);

        assertThat(modelo.archivo()).isEqualTo("order-fulfillment.bpmn");
        assertThat(EsquemaBpmn.problemas(xml)).isEmpty();
        Document documento = SecureXml.documento(new ByteArrayInputStream(xml), LimitesXml.BPMN);
        assertThat(elementos(documento, EspaciosBpmn.BPMN, "participant")).hasSize(2);
        assertThat(elementos(documento, EspaciosBpmn.BPMN, "process")).hasSize(1);
        assertThat(elementos(documento, EspaciosBpmn.BPMN, "userTask")).hasSize(2);
        assertThat(elementos(documento, EspaciosBpmn.BPMN, "endEvent")).hasSize(2);
        Element gateway = elementos(documento, EspaciosBpmn.BPMN, "exclusiveGateway").getFirst();
        Element condicion = elementos(documento, EspaciosBpmn.BPMN, "conditionExpression").getFirst();
        assertThat(condicion.getTextContent()).isEqualTo("total > 100");
        assertThat(condicion.getAttribute("language")).isEqualTo(EspaciosBpmn.CONDICIONES);
        Element porDefecto = elementos(documento, EspaciosBpmn.BPMN, "sequenceFlow").stream()
                .filter(flujo -> "No".equals(flujo.getAttribute("name"))).findFirst().orElseThrow();
        assertThat(gateway.getAttribute("default")).isEqualTo(porDefecto.getAttribute("id"));
        assertThat(elementos(documento, EspaciosBpmn.BPMN, "documentation"))
                .extracting(Element::getTextContent).contains("Checkout to delivery", "Check stock & price");

        // Cada forma del archivo es la que el modelo tiene guardada, con el mismo elemento y los mismos limites
        List<Forma> guardadas = geometriaService.obtener(empresaId, procesoId).formas();
        List<Element> formas = elementos(documento, EspaciosBpmn.BPMNDI, "BPMNShape");
        assertThat(formas).hasSameSizeAs(guardadas);
        for (Forma guardada : guardadas) {
            Element forma = formas.stream().filter(una -> guardada.elemento().equals(una.getAttribute("bpmnElement")))
                    .findFirst().orElseThrow();
            Element limites = (Element) forma.getElementsByTagNameNS(EspaciosBpmn.DC, "Bounds").item(0);
            assertThat(Double.parseDouble(limites.getAttribute("x"))).isEqualTo(guardada.limites().x());
            assertThat(Double.parseDouble(limites.getAttribute("width"))).isEqualTo(guardada.limites().ancho());
        }
        assertThat(elementos(documento, EspaciosBpmn.BPMNDI, "BPMNEdge"))
                .hasSize(geometriaService.obtener(empresaId, procesoId).aristas().size());
    }

    @Test
    @DisplayName("Publicar guarda en la version el mismo BPMN que se exporta, y la version lo devuelve igual")
    void publicar_guardaElBpmn() {
        String borrador = modeloBpmnService.exportar(empresaId, procesoId).xml();

        publicar();

        ModeloBpmn version = versionService.modelo(empresaId, procesoId, 1);
        assertThat(version.archivo()).isEqualTo("order-fulfillment-v1.bpmn");
        assertThat(version.xml()).isEqualTo(borrador);
        assertThat(EsquemaBpmn.problemas(version.xml().getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("La migracion le da a una version vieja su bpmn_id y el mismo BPMN que hoy se guarda al publicar")
    void laMigracion_daLoMismoQuePublicar() throws SQLException {
        publicar();
        Long versionId = jdbc.queryForObject(
                "select id from versiones_proceso where proceso_id = ? and numero = 1", Long.class, procesoId);
        String[] publicada = leer(versionId);

        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("select set_config('app.sistema', 'on', true)");
            }
            // Asi quedo una version publicada antes del PR 53: sin bpmn_id en su instantanea y sin BPMN
            try (PreparedStatement vieja = conexion.prepareStatement(
                    "update versiones_proceso set definicion = ?, modelo_bpmn = '' where id = ?")) {
                vieja.setString(1, sinBpmnId(publicada[0]));
                vieja.setLong(2, versionId);
                vieja.executeUpdate();
            }

            V44__bpmn_de_las_versiones.convertir(conexion, versionId);

            String[] migrada = leer(conexion, versionId);
            conexion.rollback();
            assertThat(JSON.readTree(migrada[0])).isEqualTo(JSON.readTree(publicada[0]));
            assertThat(migrada[1]).isEqualTo(publicada[1]);
        }
    }

    @Test
    @DisplayName("Lo que se exporta de un proceso con cambios sin publicar es el borrador, no la version")
    void conCambios_exportaElBorrador() {
        publicar();
        Long ventas = jdbc.queryForObject(
                "select l.id from lanes l join pools p on p.id = l.pool_id where p.proceso_id = ?", Long.class,
                procesoId);
        actividadService.crear(empresaId, adminId, ventas, "Pack order", null, TipoActividad.USUARIO, 900, 0);

        assertThat(modeloBpmnService.exportar(empresaId, procesoId).xml()).contains("name=\"Pack order\"");
        assertThat(versionService.modelo(empresaId, procesoId, 1).xml()).doesNotContain("Pack order");
    }

    private void publicar() {
        Long version = procesoService.obtener(empresaId, procesoId, false).version();
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO, version);
    }

    private String[] leer(Long versionId) throws SQLException {
        // En una transaccion: app.sistema queda en ella y no en la conexion, que vuelve al pool
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("select set_config('app.sistema', 'on', true)");
            }
            String[] leida = leer(conexion, versionId);
            conexion.commit();
            conexion.setAutoCommit(true);
            return leida;
        }
    }

    private static String[] leer(Connection conexion, Long versionId) throws SQLException {
        try (PreparedStatement consulta = conexion.prepareStatement(
                "select definicion, modelo_bpmn from versiones_proceso where id = ?")) {
            consulta.setLong(1, versionId);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return new String[] {fila.getString(1), fila.getString(2)};
            }
        }
    }

    /** La instantanea sin el bpmn_id de ningun elemento. */
    private static String sinBpmnId(String definicion) {
        JsonNode raiz = JSON.readTree(definicion);
        for (JsonNode lista : raiz) {
            if (lista.isArray()) {
                for (JsonNode elemento : lista) {
                    ((ObjectNode) elemento).remove("bpmnId");
                }
            }
        }
        String sin = JSON.writeValueAsString(raiz);
        assertThat(sin).doesNotContain("bpmnId");
        return sin;
    }

    private static List<Element> elementos(Document documento, String espacio, String nombre) {
        NodeList nodos = documento.getElementsByTagNameNS(espacio, nombre);
        List<Element> lista = new ArrayList<>();
        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }
        return lista;
    }
}
