package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.impl.MensajeServiceImpl;

/** HU-25 a HU-27: la comunicacion entre participantes, anclada a los nodos que la mandan y la esperan. */
@ExtendWith(MockitoExtension.class)
class MensajeServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private PoolRepository poolRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private CorrelacionRepository correlacionRepository;

    @Spy
    private MensajeMapper mensajeMapper = Mappers.getMapper(MensajeMapper.class);

    @InjectMocks
    private MensajeServiceImpl mensajeService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool tienda;
    private Pool transportadora;
    private Lane ventas;
    private Actividad enviar;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        tienda = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        transportadora = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Carrier").cajaNegra(true)
                .build();
        ventas = Lane.builder().id(7L).empresa(empresa).pool(tienda).nombre("Sales")
                .rolProceso(RolProceso.builder().id(20L).empresa(empresa).nombre("Sales").build()).build();
        enviar = Actividad.builder().id(30L).empresa(empresa).lane(ventas).nombre("Ship order")
                .tipoActividad(TipoActividad.ENVIO).build();
    }

    @Test
    @DisplayName("HU-25: el mensaje une dos participantes del proceso y queda anotado en el historial")
    void crear_uneDosParticipantesDelProceso() {
        prepararPools();
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        MensajeResponse respuesta = mensajeService.crear(EMPRESA, AUTOR, 100L,
                DatosDeMensaje.basico("Shipment requested", "Pedido listo", 5L, 6L));

        assertThat(respuesta.poolOrigenId()).isEqualTo(5L);
        assertThat(respuesta.poolDestinoId()).isEqualTo(6L);
        assertThat(respuesta.siFalla()).isEqualTo(AccionSiFalla.CONTINUAR);
        // Sin variable, el nombre del mensaje se convierte en una que un evaluador pueda leer.
        assertThat(respuesta.variable()).isEqualTo("shipmentRequested");
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("Shipment requested"));
    }

    @Test
    @DisplayName("Un mensaje tiene que conectar dos pools diferentes")
    void crear_mismoPool_lanzaReglaNegocio() {
        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                DatosDeMensaje.basico("Aviso", "texto", 5L, 5L)))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(procesoRepository, poolRepository, mensajeRepository);
    }

    @Test
    @DisplayName("Los dos pools de un mensaje tienen que ser participantes de su proceso")
    void crear_poolDeOtroProceso_lanzaReglaNegocio() {
        Proceso otroProceso = Proceso.builder().id(101L).empresa(empresa).nombre("Returns").build();
        Pool ajeno = Pool.builder().id(7L).empresa(empresa).proceso(otroProceso).nombre("Supplier").build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                DatosDeMensaje.basico("Aviso", "texto", 5L, 7L)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("participantes de su proceso");
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un proceso eliminado o de otra tienda no acepta mensajes")
    void crear_procesoFueraDeLaPuerta_lanzaNoEncontrado() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                DatosDeMensaje.basico("Aviso", "texto", 5L, 6L)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("R-37: el mensaje sale de un nodo que sabe enviarlo y entra en uno que sabe esperarlo")
    void crear_anclandoElEnvioYLaRecepcion_losGuarda() {
        Pool otraTienda = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Partner store").build();
        Actividad esperar = enOtroPool(otraTienda, 32L, "Await order", TipoActividad.RECEPCION);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(otraTienda));
        when(nodoFlujoRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(enviar));
        when(nodoFlujoRepository.findByIdAndEmpresaId(32L, EMPRESA)).thenReturn(Optional.of(esperar));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        MensajeResponse respuesta = mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).nodoOrigenId(30L).nodoDestinoId(32L).build());

        assertThat(respuesta.nodoOrigenId()).isEqualTo(30L);
        assertThat(respuesta.nodoDestinoId()).isEqualTo(32L);
    }

    @Test
    @DisplayName("R-37: el nodo anclado tiene que estar en el pool de su lado")
    void crear_conNodoDeOtroPool_lanzaReglaNegocio() {
        Pool otraTienda = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Partner store").build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(otraTienda));
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(nodoFlujoRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(enviar));

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).nodoOrigenId(30L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El nodo de origen del mensaje debe estar en el pool de origen.");
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    @DisplayName("R-37: una actividad de usuario no manda mensajes, y un gateway no los espera")
    void crear_conNodosQueNoIntercambianMensajes_lanzaReglaNegocio() {
        Actividad revisar = Actividad.builder().id(33L).empresa(empresa).lane(ventas).nombre("Review order")
                .tipoActividad(TipoActividad.USUARIO).build();
        Pool otraTienda = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Partner store").build();
        Lane compras = Lane.builder().id(8L).empresa(empresa).pool(otraTienda).nombre("Purchases")
                .rolProceso(RolProceso.builder().id(21L).empresa(empresa).nombre("Purchases").build()).build();
        Gateway decidir = Gateway.builder().id(34L).empresa(empresa).lane(compras).nombre("Approved?")
                .tipoGateway(TipoGateway.EXCLUSIVO).build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(otraTienda));
        when(nodoFlujoRepository.findByIdAndEmpresaId(33L, EMPRESA)).thenReturn(Optional.of(revisar));

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).nodoOrigenId(33L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El nodo de origen del mensaje debe poder enviarlo.");

        when(nodoFlujoRepository.findByIdAndEmpresaId(34L, EMPRESA)).thenReturn(Optional.of(decidir));
        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).nodoDestinoId(34L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El nodo de destino del mensaje debe poder recibirlo.");
    }

    @Test
    @DisplayName("Un evento de mensaje intermedio tambien sirve de ancla: espera la respuesta")
    void crear_anclandoUnEventoDeMensaje_loGuarda() {
        Pool otraTienda = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Partner store").build();
        Lane compras = Lane.builder().id(8L).empresa(empresa).pool(otraTienda).nombre("Purchases")
                .rolProceso(RolProceso.builder().id(21L).empresa(empresa).nombre("Purchases").build()).build();
        Evento esperar = Evento.builder().id(36L).empresa(empresa).lane(compras).nombre("Order received")
                .tipoEvento(TipoEvento.MENSAJE_INTERMEDIO).build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(otraTienda));
        when(nodoFlujoRepository.findByIdAndEmpresaId(36L, EMPRESA)).thenReturn(Optional.of(esperar));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(mensajeService.crear(EMPRESA, AUTOR, 100L, datos(5L, 6L).nodoDestinoId(36L).build())
                .nodoDestinoId()).isEqualTo(36L);
    }

    @Test
    @DisplayName("R-38: un pool de caja negra no se modela por dentro, asi que no ancla nodos")
    void crear_anclandoUnPoolDeCajaNegra_lanzaReglaNegocio() {
        prepararPools();

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).nodoDestinoId(30L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("caja negra");
        verifyNoInteractions(nodoFlujoRepository);
    }

    @Test
    @DisplayName("R-39: desviar el flujo exige decir a que actividad, y del pool que envia")
    void crear_conManejoDeError_exigeUnaActividadDelPoolQueEnvia() {
        prepararPools();

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).siFalla(AccionSiFalla.MANEJAR_ERROR).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Indique la actividad que maneja el error.");

        Pool bodegaPool = Pool.builder().id(9L).empresa(empresa).proceso(proceso).nombre("Warehouse").build();
        Actividad ajena = enOtroPool(bodegaPool, 35L, "Hold order", TipoActividad.USUARIO);
        when(actividadRepository.findByIdAndEmpresaId(35L, EMPRESA)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).siFalla(AccionSiFalla.MANEJAR_ERROR).nodoManejoErrorId(35L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("pool que envia el mensaje");
    }

    @Test
    @DisplayName("R-39: sin manejo de error no se indica la actividad que lo atiende")
    void crear_conActividadDeErrorYSinManejarlo_lanzaReglaNegocio() {
        prepararPools();

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).siFalla(AccionSiFalla.CONTINUAR).nodoManejoErrorId(30L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Solo un mensaje que maneja el error");
        verifyNoInteractions(actividadRepository);
    }

    @Test
    @DisplayName("R-40: la respuesta esperada es un mensaje que vuelve desde el pool de destino")
    void crear_conRespuestaEsperada_exigeQueVuelva() {
        Mensaje vuelve = Mensaje.builder().id(41L).empresa(empresa).proceso(proceso).poolOrigen(transportadora)
                .poolDestino(tienda).nombre("Shipment confirmation").build();
        Mensaje queNoVuelve = Mensaje.builder().id(42L).empresa(empresa).proceso(proceso).poolOrigen(tienda)
                .poolDestino(transportadora).nombre("Another request").build();
        prepararPools();
        when(mensajeRepository.findByIdAndEmpresaId(42L, EMPRESA)).thenReturn(Optional.of(queNoVuelve));

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).respuestaEsperadaId(42L).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La respuesta esperada debe ser un mensaje que vuelve desde el pool destino.");

        when(mensajeRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(vuelve));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(mensajeService.crear(EMPRESA, AUTOR, 100L, datos(5L, 6L).respuestaEsperadaId(41L).build())
                .respuestaEsperadaId()).isEqualTo(41L);
    }

    @Test
    @DisplayName("Una variable con espacios no sirve para leer el cuerpo del mensaje")
    void crear_conVariableInvalida_lanzaReglaNegocio() {
        prepararPools();

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).variable("payment result").build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("empieza por letra");
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dos campos del mensaje no pueden llamarse igual")
    void crear_conCamposRepetidos_lanzaReglaNegocio() {
        prepararPools();

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L,
                datos(5L, 6L).campos(List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                        new CampoDeMensaje("ORDERID", TipoDeDato.NUMERO))).build()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("repite el campo");
    }

    @Test
    @DisplayName("Editar no cambia los pools del mensaje: eso seria trazar otro")
    void editar_conOtroPool_lanzaReglaNegocio() {
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensajeGuardado()));

        assertThatThrownBy(() -> mensajeService.editar(EMPRESA, AUTOR, 40L,
                DatosDeMensaje.basico("Shipment requested", "Pedido listo", 9L, 6L), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no se cambian");
        verify(mensajeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Editar guarda el anclaje, el destino y la variable del mensaje")
    void editar_guardaElAnclajeYSusDatos() {
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensajeGuardado()));
        when(nodoFlujoRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(enviar));
        when(mensajeRepository.saveAndFlush(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        MensajeResponse respuesta = mensajeService.editar(EMPRESA, AUTOR, 40L,
                datos(null, null).nodoOrigenId(30L).tipoDestino(TipoDestino.COLA).variable("shipment").build(), null);

        assertThat(respuesta.nodoOrigenId()).isEqualTo(30L);
        assertThat(respuesta.tipoDestino()).isEqualTo(TipoDestino.COLA);
        assertThat(respuesta.variable()).isEqualTo("shipment");
    }

    @Test
    @DisplayName("HU-27: al eliminar un mensaje se va con el su clave de correlacion")
    void eliminar_arrastraLaCorrelacion() {
        Mensaje mensaje = mensajeGuardado();
        Correlacion correlacion = Correlacion.builder().id(50L).empresa(empresa).mensaje(mensaje)
                .criterio("orderId").build();
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensaje));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(correlacion));

        mensajeService.eliminar(EMPRESA, AUTOR, 40L);

        verify(correlacionRepository).delete(correlacion);
        verify(mensajeRepository).delete(mensaje);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("eliminado"));
    }

    private Mensaje mensajeGuardado() {
        return Mensaje.builder().id(40L).empresa(empresa).proceso(proceso).poolOrigen(tienda)
                .poolDestino(transportadora).nombre("Shipment requested").build();
    }

    private Actividad enOtroPool(Pool pool, Long id, String nombre, TipoActividad tipo) {
        Lane lane = Lane.builder().id(pool.getId() + 100).empresa(empresa).pool(pool).nombre(pool.getNombre())
                .rolProceso(RolProceso.builder().id(21L).empresa(empresa).nombre(pool.getNombre()).build()).build();
        return Actividad.builder().id(id).empresa(empresa).lane(lane).nombre(nombre).tipoActividad(tipo).build();
    }

    private void prepararPools() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(transportadora));
    }

    /** Los datos de un mensaje de prueba, para cambiar solo lo que cada caso necesita. */
    private static DatosDeMensajeBuilder datos(Long poolOrigenId, Long poolDestinoId) {
        return new DatosDeMensajeBuilder(poolOrigenId, poolDestinoId);
    }

    /** Constructor de prueba: el record es inmutable y cada caso cambia un campo distinto. */
    private static final class DatosDeMensajeBuilder {

        private final Long poolOrigenId;
        private final Long poolDestinoId;
        private Long nodoOrigenId;
        private Long nodoDestinoId;
        private TipoDestino tipoDestino;
        private AccionSiFalla siFalla;
        private Long nodoManejoErrorId;
        private List<CampoDeMensaje> campos = List.of();
        private String variable;
        private Long respuestaEsperadaId;

        private DatosDeMensajeBuilder(Long poolOrigenId, Long poolDestinoId) {
            this.poolOrigenId = poolOrigenId;
            this.poolDestinoId = poolDestinoId;
        }

        private DatosDeMensajeBuilder nodoOrigenId(Long id) {
            this.nodoOrigenId = id;
            return this;
        }

        private DatosDeMensajeBuilder nodoDestinoId(Long id) {
            this.nodoDestinoId = id;
            return this;
        }

        private DatosDeMensajeBuilder tipoDestino(TipoDestino tipo) {
            this.tipoDestino = tipo;
            return this;
        }

        private DatosDeMensajeBuilder siFalla(AccionSiFalla accion) {
            this.siFalla = accion;
            return this;
        }

        private DatosDeMensajeBuilder nodoManejoErrorId(Long id) {
            this.nodoManejoErrorId = id;
            return this;
        }

        private DatosDeMensajeBuilder campos(List<CampoDeMensaje> campos) {
            this.campos = campos;
            return this;
        }

        private DatosDeMensajeBuilder variable(String variable) {
            this.variable = variable;
            return this;
        }

        private DatosDeMensajeBuilder respuestaEsperadaId(Long id) {
            this.respuestaEsperadaId = id;
            return this;
        }

        private DatosDeMensaje build() {
            return new DatosDeMensaje("Shipment requested", "Pedido listo", poolOrigenId, poolDestinoId,
                    nodoOrigenId, nodoDestinoId, tipoDestino, siFalla, nodoManejoErrorId, false, campos, null,
                    variable, respuestaEsperadaId);
        }
    }
}
