package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.model.AristaBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Arista;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Forma;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
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
import com.facimus.procesos.modelado.service.PoolService;

import db.migration.common.V42__geometria_de_los_diagramas;

/**
 * PR 52: la geometria BPMN DI sigue al modelo sola. Cada cambio de un proceso la recalcula en su transaccion, cada
 * elemento tiene un bpmn_id que no cambia, y recalcular no sube la version de los elementos que nadie toco.
 */
@SpringBootTest
@ActiveProfiles("test")
class GeometriaIntegracionTest {

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
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long ventas;
    private Long inicio;
    private ActividadResponse recibir;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@geometria.test";
        empresaId = empresaService.registrar("Tienda de la geometria " + numero, "9058" + numero + "000-1", admin,
                "Gea Geometria", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.NINGUNA).id();
        ventas = laneService.crear(empresaId, adminId, tienda, "Sales",
                rolProcesoService.crear(empresaId, adminId, "Sales " + numero, null).id()).id();
        inicio = eventoService.crear(empresaId, adminId, ventas, "Order received", TipoEvento.MENSAJE_INICIO, 0, 0)
                .id();
        recibir = actividadService.crear(empresaId, adminId, ventas, "Receive order", null, TipoActividad.USUARIO,
                180, 0);
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir.id()));
        mensajeService.crear(empresaId, adminId, procesoId, DatosDeMensaje.basico("Order placed", "Cart", cliente,
                tienda));
    }

    @Test
    @DisplayName("Cada elemento tiene su forma o su arista, con un bpmn_id de su tipo")
    void cadaElementoTieneSuGeometria() {
        Geometria geometria = geometriaService.obtener(empresaId, procesoId);

        assertThat(geometria.formas()).extracting(Forma::tipo)
                .containsExactlyInAnyOrder("PARTICIPANTE", "PARTICIPANTE", "LANE", "EVENTO", "TAREA");
        assertThat(geometria.aristas()).extracting(Arista::tipo).containsExactlyInAnyOrder("SECUENCIA", "MENSAJE");
        assertThat(geometria.formas()).extracting(Forma::elemento)
                .anyMatch(id -> id.matches("Activity_[0-9a-f]{12}"))
                .anyMatch(id -> id.matches("Event_[0-9a-f]{12}"))
                .anyMatch(id -> id.matches("Lane_[0-9a-f]{12}"))
                .allMatch(id -> id.matches("(Participant|Lane|Activity|Event|Gateway)_[0-9a-f]{12}"));
        Forma tarea = forma(geometria, "TAREA");
        assertThat(tarea.limites().ancho()).isEqualTo(128);
        assertThat(tarea.limites().alto()).isEqualTo(56);
    }

    @Test
    @DisplayName("Mover una tarea mueve su forma y su flujo; su bpmn_id no cambia, y la version de los demas tampoco")
    void moverUnaTarea() {
        Forma antes = forma(geometriaService.obtener(empresaId, procesoId), "TAREA");
        Map<String, Long> versiones = versionesDelResto();

        actividadService.editar(empresaId, adminId, recibir.id(), "Receive order", null, TipoActividad.USUARIO, ventas,
                180, 60, recibir.version());

        Geometria despues = geometriaService.obtener(empresaId, procesoId);
        Forma movida = forma(despues, "TAREA");
        assertThat(movida.elemento()).isEqualTo(antes.elemento());
        assertThat(movida.limites().y()).isEqualTo(antes.limites().y() + 60);
        assertThat(movida.limites().x()).isEqualTo(antes.limites().x());
        Arista flujo = despues.aristas().stream().filter(arista -> arista.tipo().equals("SECUENCIA")).findFirst()
                .orElseThrow();
        assertThat(flujo.puntos().getLast().y()).isEqualTo(movida.limites().y() + movida.limites().alto() / 2);
        assertThat(versionesDelResto()).isEqualTo(versiones);
    }

    @Test
    @DisplayName("Lo que se da de baja deja de tener geometria")
    void darDeBaja() {
        actividadService.eliminar(empresaId, adminId, recibir.id());

        Geometria geometria = geometriaService.obtener(empresaId, procesoId);
        assertThat(geometria.formas()).extracting(Forma::tipo).doesNotContain("TAREA");
        assertThat(geometria.aristas()).extracting(Arista::tipo).containsExactly("MENSAJE");
    }

    @Test
    @DisplayName("Un proceso recien creado ya tiene la forma de su pool, y uno dado de baja ya no tiene ninguna")
    void crearYEliminarUnProceso() {
        Long nuevo = procesoService.crear(empresaId, adminId, "Returns", "Refunds", "Fulfillment").id();

        assertThat(geometriaService.obtener(empresaId, nuevo).formas()).extracting(Forma::tipo)
                .containsExactly("PARTICIPANTE");

        procesoService.eliminarLogico(empresaId, nuevo, adminId);

        assertThat(geometriaService.obtener(empresaId, nuevo).formas()).isEmpty();
        assertThat(geometriaService.obtener(empresaId, procesoId).formas()).isNotEmpty();
    }

    @Test
    @DisplayName("La geometria guardada es la que da la cuenta: recalcularla no cambia nada")
    void recalcular_daLoMismo() {
        Map<String, Limites> antes = porElemento(geometriaService.obtener(empresaId, procesoId));

        geometriaService.recalcular(empresaId, procesoId);

        assertThat(porElemento(geometriaService.obtener(empresaId, procesoId))).isEqualTo(antes);
    }

    @Test
    @DisplayName("La migracion de los diagramas que ya existian da la misma geometria que el servicio")
    void laMigracion_daLoMismoQueElServicio() throws Exception {
        // Una compuerta y un flujo con etiqueta, para que la cuenta pase por todas sus ramas
        Long pago = gatewayService.crear(empresaId, adminId, ventas, "Paid?", TipoGateway.EXCLUSIVO, 360, 40).id();
        Long fin = eventoService.crear(empresaId, adminId, ventas, "Done", TipoEvento.FIN, 540, 40).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir.id(), pago));
        arcoService.crear(empresaId, adminId, new DatosDeArco(pago, fin, "Yes", null, true, 0));
        Geometria delServicio = geometriaService.obtener(empresaId, procesoId);

        Geometria migrada;
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("select set_config('app.sistema', 'on', true)");
                // Como antes del PR 52: los diagramas existen, su geometria no
                sentencia.executeUpdate("delete from formas_bpmn");
                sentencia.executeUpdate("delete from aristas_bpmn");
            }
            Context contexto = mock(Context.class);
            when(contexto.getConnection()).thenReturn(conexion);
            new V42__geometria_de_los_diagramas().migrate(contexto);
            migrada = leida(conexion);
            conexion.rollback();
        }

        assertThat(delServicio.formas()).extracting(Forma::tipo).contains("COMPUERTA");
        assertThat(delServicio.aristas()).filteredOn(arista -> arista.etiqueta() != null).hasSize(1);
        assertThat(porElemento(migrada)).isEqualTo(porElemento(delServicio));
        assertThat(migrada.formas()).containsExactlyInAnyOrderElementsOf(delServicio.formas());
        assertThat(migrada.aristas()).containsExactlyInAnyOrderElementsOf(delServicio.aristas());
    }

    /** La geometria de este proceso, leida en la transaccion de la migracion. */
    private Geometria leida(Connection conexion) throws SQLException {
        List<Forma> formas = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select elemento, tipo, x, y, ancho, alto, etiqueta_x, etiqueta_y, etiqueta_ancho, etiqueta_alto
                from formas_bpmn where proceso_id = ?""")) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    formas.add(new Forma(filas.getString(1), filas.getString(2), new Limites(filas.getDouble(3),
                            filas.getDouble(4), filas.getDouble(5), filas.getDouble(6)), etiqueta(filas, 7)));
                }
            }
        }
        List<Arista> aristas = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select elemento, tipo, puntos, etiqueta_x, etiqueta_y, etiqueta_ancho, etiqueta_alto
                from aristas_bpmn where proceso_id = ?""")) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    aristas.add(new Arista(filas.getString(1), filas.getString(2),
                            AristaBpmn.builder().puntos(filas.getString(3)).build().leerPuntos(), etiqueta(filas, 4)));
                }
            }
        }
        return new Geometria(formas, aristas);
    }

    private static Limites etiqueta(ResultSet filas, int desde) throws SQLException {
        double x = filas.getDouble(desde);
        return filas.wasNull() ? null : new Limites(x, filas.getDouble(desde + 1), filas.getDouble(desde + 2),
                filas.getDouble(desde + 3));
    }

    private static Forma forma(Geometria geometria, String tipo) {
        return geometria.formas().stream().filter(forma -> forma.tipo().equals(tipo)).findFirst().orElseThrow();
    }

    private static Map<String, Limites> porElemento(Geometria geometria) {
        return geometria.formas().stream().collect(Collectors.toMap(Forma::elemento, Forma::limites));
    }

    /** La version de cada elemento que no es la tarea: recalcular la geometria no puede tocarlas. */
    private Map<String, Long> versionesDelResto() {
        return jdbc.queryForList("""
                        select bpmn_id, version from pools where proceso_id = ?
                        union all select l.bpmn_id, l.version from lanes l join pools p on p.id = l.pool_id
                            where p.proceso_id = ?
                        union all select n.bpmn_id, n.version from nodos_flujo n join lanes l on l.id = n.lane_id
                            join pools p on p.id = l.pool_id where p.proceso_id = ? and n.tipo_nodo <> 'ACTIVIDAD'
                        union all select bpmn_id, version from mensajes where proceso_id = ?
                        """, procesoId, procesoId, procesoId, procesoId).stream()
                .collect(Collectors.toMap(fila -> (String) fila.get("bpmn_id"),
                        fila -> ((Number) fila.get("version")).longValue(), (a, b) -> a, TreeMap::new));
    }
}
