package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.RefreshToken;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.Sesion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoCompartidoRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

/**
 * Flyway crea el esquema y la base hace cumplir la unicidad de nombres por su cuenta: los tests guardan con los
 * repositorios, sin pasar por la validacion de los services.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigracionesTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private ConfiguracionTiendaRepository configuracionTiendaRepository;

    @Autowired
    private RolProcesoRepository rolProcesoRepository;

    @Autowired
    private ProcesoCompartidoRepository procesoCompartidoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SesionRepository sesionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Empresa empresa;

    @BeforeEach
    void registrarEmpresa() {
        empresa = nuevaEmpresa();
    }

    @Test
    @DisplayName("Flyway aplica el esquema comun y la migracion propia del motor")
    void flyway_aplicaLasMigracionesComunYDelMotor() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getScript)
                .containsExactly("V1__esquema_inicial.sql", "V2__nombres_unicos_por_empresa.sql",
                        "V3__procesos_compartidos.sql", "V4__sesiones.sql", "V5__versiones.sql",
                        "V6__auditoria.sql", "V7__claves_idempotencia.sql", "V8__baja_logica_del_modelado.sql",
                        "V9__eventos_y_tipos_de_actividad.sql",
                        "V10__mensajes_anclados_y_correlacion_por_campo.sql",
                        "V11__salida_por_defecto_y_orden_de_los_arcos.sql",
                        "V12__versiones_publicadas_del_proceso.sql", "V13__definicion_de_la_version.sql",
                        "V14__gobierno_de_la_tienda.sql", "V15__casos_tareas_y_bitacora.sql",
                        "V16__variables_del_caso.sql",
                        "V17__membresias_de_rol.sql", "V18__reloj_de_la_tienda.sql",
                        "V19__bandejas_de_mensajes.sql", "V20__cuerpos_de_los_mensajes.sql",
                        "V21__parametros_de_simulacion.sql");
    }

    @Test
    @DisplayName("La base rechaza dos procesos activos de una empresa con el mismo nombre, sin distinguir mayusculas")
    void procesosActivos_mismoNombre_laBaseLosRechaza() {
        procesoRepository.saveAndFlush(proceso(empresa, "Order fulfillment", true));

        assertThatThrownBy(() -> procesoRepository.saveAndFlush(proceso(empresa, "ORDER FULFILLMENT", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un proceso eliminado libera su nombre, y otra empresa puede usarlo")
    void procesoEliminado_liberaSuNombre() {
        Empresa otra = nuevaEmpresa();
        procesoRepository.saveAndFlush(proceso(empresa, "Returns", false));
        procesoRepository.saveAndFlush(proceso(empresa, "Returns", true));
        procesoRepository.saveAndFlush(proceso(otra, "Returns", true));

        // Otras clases de este contexto guardan en la misma base, y tambien crean "Returns": se cuenta por empresa
        assertThat(procesoRepository.findAllByEmpresaId(empresa.getId()))
                .extracting(Proceso::getNombre, Proceso::isActivo)
                .containsExactlyInAnyOrder(tuple("Returns", false), tuple("Returns", true));
        assertThat(procesoRepository.findAllByEmpresaId(otra.getId()))
                .extracting(Proceso::getNombre)
                .containsExactly("Returns");
    }

    @Test
    @DisplayName("Una tienda estrena su reloj en cero y en manual, y la base no deja que el reloj vaya atras")
    void elReloj_empiezaEnCeroYNoRetrocede() {
        ConfiguracionTienda configuracion = configuracionTiendaRepository
                .saveAndFlush(ConfiguracionTienda.builder().empresa(empresa).build());

        assertThat(configuracion.getReloj()).isZero();
        assertThat(configuracion.getModoSimulacion()).isEqualTo(ModoSimulacion.MANUAL);

        configuracion.setReloj(-1);
        assertThatThrownBy(() -> configuracionTiendaRepository.saveAndFlush(configuracion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La base no acepta una tasa fuera de cien ni un socio que conteste en cero ticks")
    void losParametros_tienenLimites() {
        ConfiguracionTienda configuracion = configuracionTiendaRepository
                .saveAndFlush(ConfiguracionTienda.builder().empresa(empresa).build());

        configuracion.getSimulacion().setTasaRechazoPagos(101);
        assertThatThrownBy(() -> configuracionTiendaRepository.saveAndFlush(configuracion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La base tampoco acepta un socio que conteste antes de recibir")
    void losTicks_sonAlMenosUno() {
        ConfiguracionTienda configuracion = configuracionTiendaRepository
                .saveAndFlush(ConfiguracionTienda.builder().empresa(empresa).build());

        configuracion.getSimulacion().setTicksRespuestaPagos(0);
        assertThatThrownBy(() -> configuracionTiendaRepository.saveAndFlush(configuracion))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La base tambien rechaza dos roles de proceso activos con el mismo nombre en una empresa")
    void rolesActivos_mismoNombre_laBaseLosRechaza() {
        rolProcesoRepository.saveAndFlush(rol(empresa, "Warehouse", true));

        assertThatThrownBy(() -> rolProcesoRepository.saveAndFlush(rol(empresa, "warehouse", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
        rolProcesoRepository.saveAndFlush(rol(empresa, "Warehouse", false));
    }

    @Test
    @DisplayName("La base no deja compartir dos veces un proceso con la misma empresa, ni con su propia empresa")
    void procesosCompartidos_laBaseRechazaDuplicadosYLaPropiaEmpresa() {
        Proceso compartido = procesoRepository.saveAndFlush(proceso(empresa, "Shared catalog", true));
        Empresa invitada = nuevaEmpresa();
        procesoCompartidoRepository.saveAndFlush(comparticion(compartido, invitada));

        assertThatThrownBy(() -> procesoCompartidoRepository.saveAndFlush(comparticion(compartido, invitada)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> procesoCompartidoRepository.saveAndFlush(comparticion(compartido, empresa)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La base no deja repetir el codigo de una sesion ni el hash de un refresh token")
    void sesiones_laBaseRechazaCodigosYHashesRepetidos() {
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.builder()
                .empresa(empresa)
                .nombre("Usuaria de migraciones")
                .email(UUID.randomUUID() + "@migraciones.com")
                .passwordHash("hash-de-la-clave")
                .rolAcceso(RolAcceso.EDITOR)
                .build());
        Sesion sesion = sesionRepository.saveAndFlush(sesion(usuario, UUID.randomUUID().toString()));
        String hash = UUID.randomUUID().toString();
        refreshTokenRepository.saveAndFlush(refreshToken(sesion, hash));

        assertThatThrownBy(() -> sesionRepository.saveAndFlush(sesion(usuario, sesion.getCodigo())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(refreshToken(sesion, hash)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Sesion sesion(Usuario usuario, String codigo) {
        return Sesion.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .codigo(codigo)
                .fechaInicio(LocalDateTime.now())
                .build();
    }

    private static RefreshToken refreshToken(Sesion sesion, String hash) {
        LocalDateTime ahora = LocalDateTime.now();
        return RefreshToken.builder()
                .empresa(sesion.getEmpresa())
                .sesion(sesion)
                .tokenHash(hash)
                .fechaEmision(ahora)
                .fechaExpiracion(ahora.plusDays(7))
                .build();
    }

    private static ProcesoCompartido comparticion(Proceso proceso, Empresa invitada) {
        return ProcesoCompartido.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .empresaInvitada(invitada)
                .fechaCompartido(LocalDateTime.now())
                .build();
    }

    private Empresa nuevaEmpresa() {
        Empresa nueva = new Empresa();
        nueva.setNombre("Tienda de migraciones");
        nueva.setNit(UUID.randomUUID().toString().substring(0, 18));
        nueva.setCorreoContacto("contacto@migraciones.com");
        nueva.setFechaRegistro(LocalDate.now());
        return empresaRepository.save(nueva);
    }

    private static Proceso proceso(Empresa empresa, String nombre, boolean activo) {
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion("Proceso de prueba");
        proceso.setCategoria("Pruebas");
        proceso.setActivo(activo);
        return proceso;
    }

    private static RolProceso rol(Empresa empresa, String nombre, boolean activo) {
        RolProceso rol = new RolProceso();
        rol.setEmpresa(empresa);
        rol.setNombre(nombre);
        rol.setActivo(activo);
        return rol;
    }
}
