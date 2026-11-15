package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.common.auditoria.SucesoAuditable.TipoDeActor;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Fallo;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Informe;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * PR 67: la verificacion de una base restaurada contra PostgreSQL, con lo que de verdad escriben publicar y la
 * bitacora. La base de las pruebas la comparten otras clases, y alguna rompe su propia cadena a proposito: cada prueba
 * mira solo los fallos de su tienda.
 */
@SpringBootTest
@ActiveProfiles("test")
class VerificacionDeRespaldoIntegracionTest {

    private static final AtomicInteger TIENDAS = new AtomicInteger();

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
    private EventoService eventoService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private BitacoraService bitacora;
    @Autowired
    private VerificacionDeRespaldoService verificacion;
    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private Long adminId;

    @BeforeEach
    void registrarTienda() {
        int numero = TIENDAS.incrementAndGet();
        String correo = "admin" + numero + "@restaurada.test";
        empresaId = empresaService.registrar("Tienda restaurada " + numero, "9067" + numero + "000-1", correo,
                "Rosa Restaurada", correo, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(correo).orElseThrow().getId();
        // El alta del administrador llega a la bitacora despues del commit, en su propio hilo
        await().atMost(Duration.ofSeconds(10)).until(() -> registrosDeLaTienda() > 0);
    }

    @Test
    @DisplayName("Una base sin tocar pasa: la cadena de la tienda y la huella de cada version publicada")
    void baseSinTocar() {
        Long procesoId = procesoPublicable("Order fulfillment");
        publicar(procesoId);
        renombrarLaActividad(procesoId, "Review order");
        publicar(procesoId);
        anotar("PRUEBA_DE_RESPALDO");

        Informe informe = ContextoDeTienda.comoSistema(verificacion::verificar);

        assertThat(deLaTienda(informe)).isEmpty();
        assertThat(informe.tiendas()).isPositive();
        assertThat(informe.versiones()).isGreaterThanOrEqualTo(2);
        assertThat(informe.registrosDeBitacora()).isGreaterThanOrEqualTo(registrosDeLaTienda());
    }

    @Test
    @DisplayName("Un diagrama cambiado en la base despues de publicarse es un fallo de su tienda, con version y proceso")
    void diagramaCambiado() {
        Long procesoId = procesoPublicable("Returns and refunds");
        publicar(procesoId);

        jdbc.update("update versiones_proceso set definicion = replace(definicion, 'Receive order', 'Otra cosa') "
                + "where empresa_id = ? and proceso_id = ?", empresaId, procesoId);

        assertThat(deLaTienda(ContextoDeTienda.comoSistema(verificacion::verificar))).containsExactly(
                new Fallo(empresaId, "La versión 1 del proceso " + procesoId
                        + " ya no tiene la huella con que se publicó."));
    }

    @Test
    @DisplayName("Una fila de la bitacora cambiada con el trigger apagado es un fallo de su tienda, con el registro")
    void bitacoraCambiada() {
        anotar("PRUEBA_DE_RESPALDO");
        long ultimo = jdbc.queryForObject("select max(secuencia) from bitacora where empresa_id = ?", Long.class,
                empresaId);

        // Como el dueno de la tabla, que es lo unico que puede apagar el trigger
        jdbc.execute("reset role; alter table bitacora disable trigger bitacora_sin_cambios; "
                + "update bitacora set ip = '198.51.100.7' where empresa_id = " + empresaId + " and secuencia = "
                + ultimo + "; alter table bitacora enable trigger bitacora_sin_cambios");

        assertThat(deLaTienda(ContextoDeTienda.comoSistema(verificacion::verificar))).containsExactly(
                new Fallo(empresaId, "La bitácora se rompe en el registro " + ultimo
                        + ": El hash no coincide con lo que dice el registro."));
    }

    private List<Fallo> deLaTienda(Informe informe) {
        return informe.fallos().stream().filter(fallo -> empresaId.equals(fallo.empresaId())).toList();
    }

    private long registrosDeLaTienda() {
        return jdbc.queryForObject("select count(*) from bitacora where empresa_id = ?", Long.class, empresaId);
    }

    private void anotar(String accion) {
        bitacora.anotar(new SucesoAuditable(empresaId, TipoDeActor.SISTEMA, null, accion, "PROCESO", 1L, true,
                Map.of("motivo", "simulacro"), null, null, null, null,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)));
    }

    private void publicar(Long procesoId) {
        ProcesoResponse proceso = procesoService.obtener(empresaId, procesoId, false);
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO, proceso.version());
    }

    /** Lo minimo que el diagnostico da por bueno: una lane, un inicio, un paso y un fin, enlazados. */
    private Long procesoPublicable(String nombre) {
        Long procesoId = procesoService.crear(empresaId, adminId, nombre, "Proceso de la tienda", "Operations").id();
        Long pool = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(empresaId, adminId, "Ventas " + nombre, null).id();
        Long laneId = laneService.crear(empresaId, adminId, pool, "Sales", rolId).id();
        Long inicio = eventoService.crear(empresaId, adminId, laneId, "Order received", TipoEvento.INICIO, 40, 80)
                .id();
        Long recibir = actividadService.crear(empresaId, adminId, laneId, "Receive order", "Check the cart",
                TipoActividad.USUARIO, 180, 80).id();
        Long fin = eventoService.crear(empresaId, adminId, laneId, "Order accepted", TipoEvento.FIN, 340, 80).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, fin));
        return procesoId;
    }

    private void renombrarLaActividad(Long procesoId, String nombre) {
        Long pool = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long laneId = laneService.listarPorPool(empresaId, pool).getFirst().id();
        var actividad = actividadService.listarPorLane(empresaId, laneId).getFirst();
        actividadService.editar(empresaId, adminId, actividad.id(), nombre, actividad.descripcion(),
                actividad.tipoActividad(), actividad.laneId(), actividad.posicionX(), actividad.posicionY(),
                actividad.version());
    }
}
