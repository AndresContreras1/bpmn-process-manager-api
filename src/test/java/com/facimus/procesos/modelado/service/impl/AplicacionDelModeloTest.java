package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.repository.CrudRepository;

import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.formularios.CampoArmado;
import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.bpmn.InformeBpmn;
import com.facimus.procesos.modelado.bpmn.PlanBpmn;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.Asociacion;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.ExtensionXml;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsignacion;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.ExtensionXmlRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

/**
 * Aplicar un plan BPMN a un borrador por diferencias: el mismo archivo no escribe nada; cada elemento nuevo se crea
 * con lo del archivo y en el marco de posiciones del borrador; lo que cambia se edita y cuenta una vez; lo que falta
 * se da de baja en su repositorio; el pool de la organizacion conserva su bpmn_id y su tipo; dar de baja y crear roles
 * es de un administrador, y cambiar pools o lanes pasa por la politica de estructura de la tienda.
 */
@ExtendWith(MockitoExtension.class)
class AplicacionDelModeloTest {

    private static final Long EMPRESA_ID = 1L;
    private static final Long USUARIO_ID = 2L;
    private static final Long PROCESO_ID = 10L;

    private static final String TIENDA = "Participant_Tienda";
    /** El pool de la organizacion en el archivo: cae en el del borrador, con el bpmn_id de siempre. */
    private static final String TIENDA_EN_ARCHIVO = "Participant_Archivo";
    private static final String CLIENTE = "Participant_Cliente";
    private static final String BANCO = "Participant_Banco";
    private static final String VENTAS = "Lane_Ventas";
    private static final String ALMACEN = "Lane_Almacen";
    private static final String INICIO = "Event_Inicio";
    private static final String COBRAR = "Activity_Cobrar";
    private static final String APROBADO = "Gateway_Aprobado";
    private static final String PLAZO = "Event_Plazo";
    private static final String REVISAR = "Activity_Revisar";
    private static final String VERIFICAR = "Activity_Verificar";
    private static final String EMPACAR = "Activity_Empacar";
    private static final String FIN = "Event_Fin";
    private static final String NOTA = "TextAnnotation_Nota";
    private static final String GRUPO = "Group_Suelto";
    private static final String ASOCIACION = "Association_Nota";
    private static final String PEDIDO = "MessageFlow_Pedido";
    private static final String CONFIRMACION = "MessageFlow_Confirmacion";
    private static final String AVISO = "MessageFlow_Aviso";

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private LaneRepository laneRepository;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private GatewayRepository gatewayRepository;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private MensajeRepository mensajeRepository;

    @Mock
    private CorrelacionRepository correlacionRepository;

    @Mock
    private ExtensionXmlRepository extensionXmlRepository;

    @Mock
    private ArtefactoRepository artefactoRepository;

    @Mock
    private AsociacionRepository asociacionRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private RolProcesoRepository rolProcesoRepository;

    @Mock
    private RolProcesoService rolProcesoService;

    @Mock
    private ConfiguracionTiendaService configuracionTiendaService;

    @InjectMocks
    private AplicacionDelModelo aplicacion;

    @Captor
    private ArgumentCaptor<List<ExtensionXml>> extensionesGuardadas;

    // ---------- El borrador ----------

    private final List<Pool> pools = new ArrayList<>();
    private final List<Lane> lanes = new ArrayList<>();
    private final List<Actividad> actividades = new ArrayList<>();
    private final List<Gateway> gateways = new ArrayList<>();
    private final List<Evento> eventos = new ArrayList<>();
    private final List<Arco> arcos = new ArrayList<>();
    private final List<Mensaje> mensajes = new ArrayList<>();
    private final List<Artefacto> artefactos = new ArrayList<>();
    private final List<Asociacion> asociaciones = new ArrayList<>();
    /** Los roles de la tienda, por nombre en minusculas. */
    private final Map<String, RolProceso> roles = new HashMap<>();
    /** Las correlaciones del borrador, por id del mensaje. */
    private final Map<Long, Correlacion> correlaciones = new HashMap<>();
    private final AtomicLong siguienteId = new AtomicLong(1000);

    private Empresa tienda;
    private Proceso proceso;
    private RolProceso rolVentas;
    private RolProceso rolAlmacen;
    private Pool poolTienda;
    private Pool poolCliente;
    private Lane laneVentas;
    private Lane laneAlmacen;
    private Evento inicio;
    private Actividad cobrar;
    private Gateway aprobado;
    private Evento plazo;
    private Actividad revisar;
    private Actividad verificar;
    private Actividad empacar;
    private Evento fin;
    private Arco flujo3;
    private Arco flujo4;
    private Arco flujo5;
    private Mensaje pedido;
    private Mensaje confirmacion;
    private Mensaje aviso;
    private Correlacion correlacionPedido;
    private Correlacion correlacionConfirmacion;
    private Artefacto nota;
    private Artefacto grupo;
    private Asociacion asociacion;

    // ---------- El plan: al empezar, el mismo borrador escrito como lo deja un archivo ----------

    private final List<PlanBpmn.Pool> planPools = new ArrayList<>();
    private final List<PlanBpmn.Lane> planLanes = new ArrayList<>();
    private final Map<String, NodoPlan> planNodos = new LinkedHashMap<>();
    private final List<PlanBpmn.Arco> planArcos = new ArrayList<>();
    private final Map<String, MensajePlan> planMensajes = new LinkedHashMap<>();
    private final List<PlanBpmn.Artefacto> planArtefactos = new ArrayList<>();
    private final List<PlanBpmn.Asociacion> planAsociaciones = new ArrayList<>();
    private final Map<String, String> extensiones = new LinkedHashMap<>();
    private ModoProceso modo;

    /**
     * La tienda con su pool y el de un cliente, dos lanes, un subproceso con una tarea dentro, un evento de borde,
     * cinco flujos, tres mensajes (uno espera la respuesta de otro), dos correlaciones, una nota en una lane, un
     * grupo suelto y una asociacion. La menor x del dibujo es 100; la menor y de Ventas es 40 y la de Almacen 300.
     */
    @BeforeEach
    void elBorradorYElMismoPlan() {
        tienda = Empresa.builder().id(EMPRESA_ID).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO_ID).empresa(tienda).nombre("Order fulfillment").build();
        rolVentas = rol(7L, "Sales");
        rolAlmacen = rol(8L, "Warehouse");

        poolTienda = pool(1L, TIENDA, "Demo Store", TipoParticipante.EMPRESA, Integracion.NINGUNA, 0, false);
        poolCliente = pool(2L, CLIENTE, "Customer", TipoParticipante.CLIENTE, Integracion.CLIENTE, 1, true);
        laneVentas = lane(11L, VENTAS, "Sales", 0, poolTienda, rolVentas);
        laneAlmacen = lane(12L, ALMACEN, "Warehouse", 1, poolTienda, rolAlmacen);

        inicio = evento(21L, INICIO, "Order received", TipoEvento.INICIO, laneVentas, 100, 50);
        cobrar = actividad(22L, COBRAR, "Charge order", TipoActividad.SERVICIO, laneVentas, 200, 40);
        cobrar.setDescripcion("Charges the card");
        aprobado = gateway(23L, APROBADO, "Approved?", TipoGateway.EXCLUSIVO, laneVentas, 350, 45);
        // Mas arriba que el resto de Ventas: si contara para el marco, la lane Ventas entera se moveria
        plazo = evento(24L, PLAZO, "One hour", TipoEvento.TEMPORIZADOR_BORDE, laneVentas, 230, 30);
        plazo.setAdjuntoA(cobrar);
        plazo.setTipoTemporizador(TipoTemporizador.DURACION);
        plazo.setTemporizador("PT1H");
        plazo.setInterrumpe(true);
        // Un subproceso con contenido, mas a la izquierda y mas arriba que el resto: tampoco marca el dibujo
        revisar = actividad(25L, REVISAR, "Review fraud", TipoActividad.SUBPROCESO, laneAlmacen, 90, 280);
        verificar = actividad(26L, VERIFICAR, "Verify address", TipoActividad.USUARIO, laneAlmacen, 420, 320);
        verificar.setPadre(revisar);
        empacar = actividad(27L, EMPACAR, "Pack items", TipoActividad.USUARIO, laneAlmacen, 400, 300);
        fin = evento(28L, FIN, "Order shipped", TipoEvento.FIN, laneAlmacen, 550, 310);

        arco(31L, "Flow_1", inicio, cobrar, null, null, false, 0);
        arco(32L, "Flow_2", cobrar, aprobado, null, null, false, 1);
        flujo3 = arco(33L, "Flow_3", aprobado, revisar, "Yes", "payment.ok", false, 2);
        flujo4 = arco(34L, "Flow_4", aprobado, fin, null, null, true, 3);
        flujo5 = arco(35L, "Flow_5", empacar, fin, null, null, false, 4);

        pedido = mensaje(41L, PEDIDO, "Order placed", poolCliente, poolTienda);
        pedido.setContenido("The cart");
        pedido.setNodoDestino(inicio);
        pedido.setOrigenExterno(true);
        pedido.setCampos(List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO)));
        pedido.setUsoDeLosDatos("Opens the case");
        pedido.setVariable("orderPlaced");
        confirmacion = mensaje(42L, CONFIRMACION, "Order confirmation", poolTienda, poolCliente);
        confirmacion.setContenido("Tracking number");
        confirmacion.setNodoOrigen(fin);
        confirmacion.setTipoDestino(TipoDestino.CORREO);
        confirmacion.setSiFalla(AccionSiFalla.MANEJAR_ERROR);
        confirmacion.setNodoManejoError(aprobado);
        confirmacion.setVariable("orderConfirmation");
        aviso = mensaje(43L, AVISO, "Payment notice", poolTienda, poolCliente);
        aviso.setContenido("Payment received");
        aviso.setNodoOrigen(cobrar);
        aviso.setTipoDestino(TipoDestino.SERVICIO_WEB);
        aviso.setVariable("paymentNotice");
        pedido.setRespuestaEsperada(confirmacion);
        correlacionPedido = correlacion(51L, pedido, "order.id", "orderId", PoliticaSinCaso.INICIAR_CASO);
        correlacionConfirmacion = correlacion(52L, confirmacion, "order.id", null, PoliticaSinCaso.DESCARTAR);

        // Mas arriba que los nodos de Ventas: el marco de una lane con nodos no lo ponen sus artefactos
        nota = artefacto(61L, NOTA, TipoArtefacto.ANOTACION, "Check fraud", poolTienda, laneVentas, 150, 20, 100, 30);
        grupo = artefacto(62L, GRUPO, TipoArtefacto.GRUPO, "Exceptions", null, null, 20, 500, 300, 200);
        asociacion = Asociacion.builder().id(71L).empresa(tienda).proceso(proceso).bpmnId(ASOCIACION)
                .tipo(TipoAsociacion.ASOCIACION).origen(NOTA).destino(COBRAR).direccion(DireccionAsociacion.NINGUNA)
                .build();
        asociaciones.add(asociacion);

        planPools.add(new PlanBpmn.Pool(TIENDA_EN_ARCHIVO, "Demo Store", TipoParticipante.EMPRESA,
                Integracion.NINGUNA, 0, false));
        planPools.add(new PlanBpmn.Pool(CLIENTE, "Customer", TipoParticipante.CLIENTE, Integracion.CLIENTE, 1, true));
        planLanes.add(new PlanBpmn.Lane(VENTAS, TIENDA_EN_ARCHIVO, "Sales", "Sales", 0));
        // El rol se busca sin importar mayusculas
        planLanes.add(new PlanBpmn.Lane(ALMACEN, TIENDA_EN_ARCHIVO, "Warehouse", "warehouse", 1));

        nodoPlan(NodoPlan.evento(INICIO, VENTAS, TipoEvento.INICIO, "Order received", 0, 10));
        nodoPlan(NodoPlan.actividad(COBRAR, VENTAS, TipoActividad.SERVICIO, "Charge order", 100, 0))
                .descripcion = "Charges the card";
        nodoPlan(NodoPlan.gateway(APROBADO, VENTAS, TipoGateway.EXCLUSIVO, "Approved?", 250, 5));
        // La posicion de un evento de borde y la de un subproceso con contenido no dicen nada
        NodoPlan elPlazo = nodoPlan(NodoPlan.evento(PLAZO, VENTAS, TipoEvento.TEMPORIZADOR_BORDE, "One hour", 777, 777));
        elPlazo.adjuntoA = COBRAR;
        elPlazo.tipoTemporizador = TipoTemporizador.DURACION;
        elPlazo.temporizador = "PT1H";
        elPlazo.interrumpe = true;
        nodoPlan(NodoPlan.actividad(REVISAR, ALMACEN, TipoActividad.SUBPROCESO, "Review fraud", 555, 555));
        nodoPlan(NodoPlan.actividad(VERIFICAR, ALMACEN, TipoActividad.USUARIO, "Verify address", 320, 20)).padre =
                REVISAR;
        nodoPlan(NodoPlan.actividad(EMPACAR, ALMACEN, TipoActividad.USUARIO, "Pack items", 300, 0));
        nodoPlan(NodoPlan.evento(FIN, ALMACEN, TipoEvento.FIN, "Order shipped", 450, 10));

        planArcos.add(new PlanBpmn.Arco("Flow_1", TIENDA_EN_ARCHIVO, INICIO, COBRAR, null, null, false, 0));
        planArcos.add(new PlanBpmn.Arco("Flow_2", TIENDA_EN_ARCHIVO, COBRAR, APROBADO, null, null, false, 1));
        planArcos.add(new PlanBpmn.Arco("Flow_3", TIENDA_EN_ARCHIVO, APROBADO, REVISAR, "Yes", "payment.ok", false, 2));
        planArcos.add(new PlanBpmn.Arco("Flow_4", TIENDA_EN_ARCHIVO, APROBADO, FIN, null, null, true, 3));
        planArcos.add(new PlanBpmn.Arco("Flow_5", TIENDA_EN_ARCHIVO, EMPACAR, FIN, null, null, false, 4));

        MensajePlan elPedido = mensajePlan(new MensajePlan(PEDIDO, "Order placed", CLIENTE, TIENDA_EN_ARCHIVO));
        elPedido.contenido = "The cart";
        elPedido.nodoDestino = INICIO;
        elPedido.origenExterno = true;
        elPedido.campos = List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO));
        elPedido.usoDeLosDatos = "Opens the case";
        elPedido.variable = "orderPlaced";
        elPedido.respuesta = CONFIRMACION;
        elPedido.correlacion = new PlanBpmn.Correlacion("order.id", "orderId", PoliticaSinCaso.INICIAR_CASO);
        MensajePlan laConfirmacion = mensajePlan(new MensajePlan(CONFIRMACION, "Order confirmation",
                TIENDA_EN_ARCHIVO, CLIENTE));
        laConfirmacion.contenido = "Tracking number";
        laConfirmacion.nodoOrigen = FIN;
        laConfirmacion.tipoDestino = TipoDestino.CORREO;
        laConfirmacion.siFalla = AccionSiFalla.MANEJAR_ERROR;
        laConfirmacion.manejoError = APROBADO;
        laConfirmacion.variable = "orderConfirmation";
        laConfirmacion.correlacion = new PlanBpmn.Correlacion("order.id", null, PoliticaSinCaso.DESCARTAR);
        MensajePlan elAviso = mensajePlan(new MensajePlan(AVISO, "Payment notice", TIENDA_EN_ARCHIVO, CLIENTE));
        elAviso.contenido = "Payment received";
        elAviso.nodoOrigen = COBRAR;
        elAviso.tipoDestino = TipoDestino.SERVICIO_WEB;
        elAviso.variable = "paymentNotice";

        planArtefactos.add(new PlanBpmn.Artefacto(NOTA, TipoArtefacto.ANOTACION, "Check fraud", TIENDA_EN_ARCHIVO,
                VENTAS, 50, -20, 100, 30));
        planArtefactos.add(new PlanBpmn.Artefacto(GRUPO, TipoArtefacto.GRUPO, "Exceptions", null, null, 0, 0, 300,
                200));
        planAsociaciones.add(new PlanBpmn.Asociacion(ASOCIACION, TipoAsociacion.ASOCIACION, NOTA, COBRAR,
                DireccionAsociacion.NINGUNA));
    }

    // ---------- El mismo archivo ----------

    @Test
    @DisplayName("Importar lo mismo que el borrador ya tiene no crea, no edita ni da de baja nada")
    void mismoArchivo_noCambiaNada() {
        preparar();

        AplicacionDelModelo.Cambios cambios = aplicar(RolAcceso.EDITOR);

        assertThat(cambios).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 0));
        nadaSeGuardoNiSeBorro();
        verifyNoInteractions(configuracionTiendaService, rolProcesoService, procesoRepository);
        assertThat(plazo.getPosicionX()).isEqualTo(230);
        assertThat(plazo.getPosicionY()).isEqualTo(30);
        assertThat(revisar.getPosicionX()).isEqualTo(90);
        assertThat(revisar.getPosicionY()).isEqualTo(280);
        assertThat(pedido.getRespuestaEsperada()).isSameAs(confirmacion);
        assertThat(proceso.getModo()).isEqualTo(ModoProceso.EJECUTABLE);
    }

    @Test
    @DisplayName("Un archivo con el mismo modo que el proceso no lo edita")
    void mismoModo_noEsUnaEdicion() {
        preparar();
        modo = ModoProceso.EJECUTABLE;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 0));
        assertThat(proceso.getModo()).isEqualTo(ModoProceso.EJECUTABLE);
    }

    @Test
    @DisplayName("El modo que dice el archivo reemplaza el del proceso y cuenta como una edicion")
    void otroModo_cambiaElDelProceso() {
        preparar();
        modo = ModoProceso.DOCUMENTAL;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(proceso.getModo()).isEqualTo(ModoProceso.DOCUMENTAL);
    }

    @Test
    @DisplayName("El total de los cambios suma creados, editados y dados de baja")
    void total_sumaLasTresCuentas() {
        assertThat(new AplicacionDelModelo.Cambios(1, 2, 4).total()).isEqualTo(7);
    }

    // ---------- Permisos ----------

    @Test
    @DisplayName("Un editor no puede importar un archivo que deja fuera elementos: dice cuantos y no toca nada")
    void editor_queDejaFueraElementos_noPuede() {
        borrador();
        quitar(planPools, PlanBpmn.Pool::bpmnId, CLIENTE);
        quitar(planLanes, PlanBpmn.Lane::bpmnId, ALMACEN);
        planNodos.remove(FIN);
        quitar(planArcos, PlanBpmn.Arco::bpmnId, "Flow_5");
        planMensajes.remove(AVISO);
        quitar(planArtefactos, PlanBpmn.Artefacto::bpmnId, GRUPO);
        quitar(planAsociaciones, PlanBpmn.Asociacion::bpmnId, ASOCIACION);

        assertThatThrownBy(() -> aplicar(RolAcceso.EDITOR)).isInstanceOf(SinPermisoException.class)
                .hasMessage("El archivo deja fuera 7 elementos del borrador, y dar de baja es de un administrador. "
                        + "Importa un archivo que los tenga, o pide a un administrador que lo importe.");
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        nadaSeGuardoNiSeBorro();
        verifyNoInteractions(rolProcesoRepository, extensionXmlRepository);
    }

    @Test
    @DisplayName("Si deja fuera un solo elemento lo dice en singular, y sin tocar pools ni lanes no pide la politica")
    void editor_queDejaFueraUnElemento_loDiceEnSingular() {
        borrador();
        quitar(planAsociaciones, PlanBpmn.Asociacion::bpmnId, ASOCIACION);

        assertThatThrownBy(() -> aplicar(RolAcceso.EDITOR)).isInstanceOf(SinPermisoException.class)
                .hasMessageStartingWith("El archivo deja fuera 1 elemento del borrador,");
        verifyNoInteractions(configuracionTiendaService);
        verify(asociacionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Un editor puede importar un archivo que solo agrega, y un nodo nuevo no pide la politica de estructura")
    void editor_queSoloAgrega_puedeImportar() {
        preparar();
        guardaConId(gatewayRepository, Gateway.class, Gateway::setId);
        nodoPlan(NodoPlan.gateway("Gateway_Juntar", VENTAS, TipoGateway.PARALELO, "Join", 50, 20));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 0, 0));
        verifyNoInteractions(configuracionTiendaService);
    }

    @Test
    @DisplayName("Un editor no puede importar lanes con roles que la tienda no tiene: los nombra y no crea nada")
    void editor_conRolesQueFaltan_noPuede() {
        borrador();
        rolesDeLaTienda();
        planLanes.add(new PlanBpmn.Lane("Lane_Picking", TIENDA_EN_ARCHIVO, "Picking", "Picking", 2));
        planLanes.add(new PlanBpmn.Lane("Lane_Returns", TIENDA_EN_ARCHIVO, "Returns", "Returns", 3));

        assertThatThrownBy(() -> aplicar(RolAcceso.EDITOR)).isInstanceOf(SinPermisoException.class)
                .hasMessage("Las lanes del archivo piden roles que la organizacion no tiene: Picking, Returns. Solo un "
                        + "administrador puede crearlos: que los cree, o que importe el archivo.");
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        verifyNoInteractions(rolProcesoService);
        verify(laneRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un administrador que importa lanes con roles nuevos los crea y la lane queda con el suyo")
    void administrador_conRolesQueFaltan_losCreaYLosUsa() {
        preparar();
        guardaConId(laneRepository, Lane.class, Lane::setId);
        RolProceso picking = RolProceso.builder().id(9L).empresa(tienda).nombre("Picking").build();
        given(rolProcesoService.crear(EMPRESA_ID, USUARIO_ID, "Picking", "Creado al importar un archivo BPMN."))
                .willReturn(new RolProcesoVistaResponse(9L, "Picking", null, 0, false, 0L, USUARIO_ID, null, null,
                        null));
        given(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(9L, EMPRESA_ID)).willReturn(Optional.of(picking));
        planLanes.add(new PlanBpmn.Lane("Lane_Picking", TIENDA_EN_ARCHIVO, "Picking", "Picking", 2));

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 0, 0));
        ArgumentCaptor<Lane> guardada = ArgumentCaptor.forClass(Lane.class);
        verify(laneRepository).save(guardada.capture());
        assertThat(guardada.getValue().getRolProceso()).isSameAs(picking);
        assertThat(guardada.getValue().getPool()).isSameAs(poolTienda);
    }

    // ---------- Pools ----------

    @Test
    @DisplayName("Un pool nuevo se crea con lo que dice el archivo y pasa por la politica de estructura")
    void poolNuevo_seCreaConLoDelArchivo() {
        preparar();
        guardaConId(poolRepository, Pool.class, Pool::setId);
        planPools.add(new PlanBpmn.Pool(BANCO, "Bank", TipoParticipante.SISTEMA_EXTERNO, Integracion.PAGOS, 2, true));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 0, 0));
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        ArgumentCaptor<Pool> guardado = ArgumentCaptor.forClass(Pool.class);
        verify(poolRepository).save(guardado.capture());
        Pool banco = guardado.getValue();
        assertThat(banco.getEmpresa()).isSameAs(tienda);
        assertThat(banco.getProceso()).isSameAs(proceso);
        assertThat(banco.getBpmnId()).isEqualTo(BANCO);
        assertThat(banco.getNombre()).isEqualTo("Bank");
        assertThat(banco.getTipoParticipante()).isEqualTo(TipoParticipante.SISTEMA_EXTERNO);
        assertThat(banco.getIntegracion()).isEqualTo(Integracion.PAGOS);
        assertThat(banco.getOrden()).isEqualTo(2);
        assertThat(banco.isCajaNegra()).isTrue();
    }

    @Test
    @DisplayName("Un pool que cambia toma del archivo su nombre, tipo, integracion, orden y si es caja negra")
    void poolEditado_tomaLoDelArchivo() {
        preparar();
        cambiar(planPools, PlanBpmn.Pool::bpmnId, new PlanBpmn.Pool(CLIENTE, "Shopper", TipoParticipante.PROVEEDOR,
                Integracion.NOTIFICACIONES, 4, false));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        assertThat(poolCliente.getNombre()).isEqualTo("Shopper");
        assertThat(poolCliente.getTipoParticipante()).isEqualTo(TipoParticipante.PROVEEDOR);
        assertThat(poolCliente.getIntegracion()).isEqualTo(Integracion.NOTIFICACIONES);
        assertThat(poolCliente.getOrden()).isEqualTo(4);
        assertThat(poolCliente.isCajaNegra()).isFalse();
        verify(poolRepository, never()).save(any());
    }

    @Test
    @DisplayName("El pool de la organizacion toma nombre e integracion, pero sigue siendo la empresa y no caja negra")
    void poolDeLaOrganizacion_conservaSuTipo() {
        preparar();
        cambiar(planPools, PlanBpmn.Pool::bpmnId, new PlanBpmn.Pool(TIENDA_EN_ARCHIVO, "My store",
                TipoParticipante.PROVEEDOR, Integracion.PAGOS, 0, true));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(poolTienda.getBpmnId()).isEqualTo(TIENDA);
        assertThat(poolTienda.getNombre()).isEqualTo("My store");
        assertThat(poolTienda.getIntegracion()).isEqualTo(Integracion.PAGOS);
        assertThat(poolTienda.getTipoParticipante()).isEqualTo(TipoParticipante.EMPRESA);
        assertThat(poolTienda.isCajaNegra()).isFalse();
    }

    @Test
    @DisplayName("Un pool sin nombre en el archivo conserva el suyo: solo eso no es un cambio")
    void poolSinNombreEnElArchivo_conservaElSuyo() {
        Pool banco = pool(3L, BANCO, "Bank", TipoParticipante.SISTEMA_EXTERNO, Integracion.PAGOS, 2, true);
        preparar();
        cambiar(planPools, PlanBpmn.Pool::bpmnId, new PlanBpmn.Pool(CLIENTE, null, TipoParticipante.CLIENTE,
                Integracion.CLIENTE, 1, true));
        planPools.add(new PlanBpmn.Pool(BANCO, null, TipoParticipante.SISTEMA_EXTERNO, Integracion.PAGOS, 3, true));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(poolCliente.getNombre()).isEqualTo("Customer");
        assertThat(banco.getNombre()).isEqualTo("Bank");
        assertThat(banco.getOrden()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un pool del borrador que el archivo no tiene se da de baja y pasa por la politica de estructura")
    void poolQueSobra_seDaDeBaja() {
        Pool banco = pool(3L, BANCO, "Bank", TipoParticipante.SISTEMA_EXTERNO, Integracion.PAGOS, 2, true);
        preparar();

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 1));
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        verify(poolRepository).delete(banco);
        verify(poolRepository, never()).delete(poolTienda);
    }

    // ---------- Lanes ----------

    @Test
    @DisplayName("Una lane nueva se crea con su rol, y sus nodos usan la x del dibujo y su propia y")
    void laneNueva_conSuNodo() {
        preparar();
        guardaConId(laneRepository, Lane.class, Lane::setId);
        guardaConId(actividadRepository, Actividad.class, Actividad::setId);
        planLanes.add(new PlanBpmn.Lane("Lane_Devoluciones", TIENDA_EN_ARCHIVO, "Returns", "Warehouse", 2));
        nodoPlan(NodoPlan.actividad("Activity_Devolver", "Lane_Devoluciones", TipoActividad.USUARIO, "Refund", 50,
                15));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(2, 0, 0));
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        ArgumentCaptor<Lane> guardada = ArgumentCaptor.forClass(Lane.class);
        verify(laneRepository).save(guardada.capture());
        Lane devoluciones = guardada.getValue();
        assertThat(devoluciones.getEmpresa()).isSameAs(tienda);
        assertThat(devoluciones.getBpmnId()).isEqualTo("Lane_Devoluciones");
        assertThat(devoluciones.getNombre()).isEqualTo("Returns");
        assertThat(devoluciones.getOrden()).isEqualTo(2);
        assertThat(devoluciones.getPool()).isSameAs(poolTienda);
        assertThat(devoluciones.getRolProceso()).isSameAs(rolAlmacen);
        ArgumentCaptor<Actividad> nodo = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository).save(nodo.capture());
        assertThat(nodo.getValue().getLane()).isSameAs(devoluciones);
        assertThat(nodo.getValue().getPosicionX()).isEqualTo(150);
        assertThat(nodo.getValue().getPosicionY()).isEqualTo(15);
    }

    @Test
    @DisplayName("Una lane que cambia toma nombre, orden, pool y rol del archivo, con la politica de estructura")
    void laneEditada_tomaLoDelArchivo() {
        preparar();
        cambiar(planLanes, PlanBpmn.Lane::bpmnId, new PlanBpmn.Lane(VENTAS, CLIENTE, "Online sales", "Warehouse", 5));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        assertThat(laneVentas.getNombre()).isEqualTo("Online sales");
        assertThat(laneVentas.getOrden()).isEqualTo(5);
        assertThat(laneVentas.getPool()).isSameAs(poolCliente);
        assertThat(laneVentas.getRolProceso()).isSameAs(rolAlmacen);
    }

    @Test
    @DisplayName("Una lane del borrador que el archivo no tiene se da de baja y pasa por la politica de estructura")
    void laneQueSobra_seDaDeBaja() {
        Lane vieja = lane(13L, "Lane_Vieja", "Old", 2, poolTienda, rolVentas);
        preparar();

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 1));
        verify(configuracionTiendaService).exigirPuedeEditarEstructura(EMPRESA_ID, USUARIO_ID, PROCESO_ID);
        verify(laneRepository).delete(vieja);
    }

    @Test
    @DisplayName("Una lane sin nodos toma el marco de sus artefactos, que asi no se mueven")
    void laneSoloConArtefactos_tomaElMarcoDeEllos() {
        Lane soporte = lane(14L, "Lane_Soporte", "Support", 2, poolTienda, rolVentas);
        artefacto(63L, "TextAnnotation_Soporte1", TipoArtefacto.ANOTACION, "Call", poolTienda, soporte, 300, 700, 100,
                30);
        artefacto(64L, "TextAnnotation_Soporte2", TipoArtefacto.ANOTACION, "Mail", poolTienda, soporte, 320, 720, 100,
                30);
        preparar();
        planLanes.add(new PlanBpmn.Lane("Lane_Soporte", TIENDA_EN_ARCHIVO, "Support", "Sales", 2));
        planArtefactos.add(new PlanBpmn.Artefacto("TextAnnotation_Soporte1", TipoArtefacto.ANOTACION, "Call",
                TIENDA_EN_ARCHIVO, "Lane_Soporte", 200, 0, 100, 30));
        planArtefactos.add(new PlanBpmn.Artefacto("TextAnnotation_Soporte2", TipoArtefacto.ANOTACION, "Mail",
                TIENDA_EN_ARCHIVO, "Lane_Soporte", 220, 20, 100, 30));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 0));
        verify(artefactoRepository, never()).save(any());
    }

    // ---------- Nodos ----------

    @Test
    @DisplayName("Una actividad nueva se crea con lo del archivo, en la x del dibujo y la y de su lane")
    void nodosNuevos_laActividadSeCreaEnElMarcoDeSuLane() {
        tresNodosNuevos();

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(3, 0, 0));
        ArgumentCaptor<Actividad> actividad = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository).save(actividad.capture());
        Actividad avisar = actividad.getValue();
        assertThat(avisar.getEmpresa()).isSameAs(tienda);
        assertThat(avisar.getBpmnId()).isEqualTo("Activity_Avisar");
        assertThat(avisar.getNombre()).isEqualTo("Notify customer");
        assertThat(avisar.getDescripcion()).isEqualTo("Sends an email");
        assertThat(avisar.getTipoActividad()).isEqualTo(TipoActividad.ENVIO);
        assertThat(avisar.getLane()).isSameAs(laneVentas);
        assertThat(avisar.getPosicionX()).isEqualTo(250);
        assertThat(avisar.getPosicionY()).isEqualTo(100);
        assertThat(avisar.getProcesoLlamado()).isNull();
        verifyNoInteractions(procesoRepository);
    }

    @Test
    @DisplayName("Un gateway nuevo se crea con lo del archivo, en la x del dibujo y la y de su lane")
    void nodosNuevos_elGatewaySeCreaEnElMarcoDeSuLane() {
        tresNodosNuevos();

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(3, 0, 0));
        ArgumentCaptor<Gateway> gateway = ArgumentCaptor.forClass(Gateway.class);
        verify(gatewayRepository).save(gateway.capture());
        Gateway juntar = gateway.getValue();
        assertThat(juntar.getEmpresa()).isSameAs(tienda);
        assertThat(juntar.getBpmnId()).isEqualTo("Gateway_Juntar");
        assertThat(juntar.getNombre()).isEqualTo("Join");
        assertThat(juntar.getTipoGateway()).isEqualTo(TipoGateway.PARALELO);
        assertThat(juntar.getLane()).isSameAs(laneAlmacen);
        assertThat(juntar.getPosicionX()).isEqualTo(110);
        assertThat(juntar.getPosicionY()).isEqualTo(305);
    }

    @Test
    @DisplayName("Un evento nuevo se crea con lo del archivo y su temporizador, en la x del dibujo y la y de su lane")
    void nodosNuevos_elEventoSeCreaEnElMarcoDeSuLane() {
        tresNodosNuevos();

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(3, 0, 0));
        ArgumentCaptor<Evento> evento = ArgumentCaptor.forClass(Evento.class);
        verify(eventoRepository).save(evento.capture());
        Evento laEspera = evento.getValue();
        assertThat(laEspera.getEmpresa()).isSameAs(tienda);
        assertThat(laEspera.getBpmnId()).isEqualTo("Event_Espera");
        assertThat(laEspera.getNombre()).isEqualTo("Wait a day");
        assertThat(laEspera.getTipoEvento()).isEqualTo(TipoEvento.TEMPORIZADOR_INTERMEDIO);
        assertThat(laEspera.getTipoTemporizador()).isEqualTo(TipoTemporizador.DURACION);
        assertThat(laEspera.getTemporizador()).isEqualTo("P1D");
        assertThat(laEspera.getInterrumpe()).isFalse();
        assertThat(laEspera.getLane()).isSameAs(laneAlmacen);
        assertThat(laEspera.getPosicionX()).isEqualTo(120);
        assertThat(laEspera.getPosicionY()).isEqualTo(307);
        verifyNoInteractions(procesoRepository);
    }

    @Test
    @DisplayName("Una llamada se queda con el proceso que nombra solo si es de la tienda")
    void llamadas_soloAProcesosDeLaTienda() {
        preparar();
        guardaConId(actividadRepository, Actividad.class, Actividad::setId);
        Proceso facturacion = Proceso.builder().id(99L).empresa(tienda).nombre("Invoicing").build();
        given(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(99L, EMPRESA_ID)).willReturn(Optional.of(facturacion));
        given(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(98L, EMPRESA_ID)).willReturn(Optional.empty());
        nodoPlan(NodoPlan.actividad("Activity_Facturar", VENTAS, TipoActividad.LLAMADA, "Invoice", 400, 0))
                .procesoLlamado = 99L;
        nodoPlan(NodoPlan.actividad("Activity_Ajena", VENTAS, TipoActividad.LLAMADA, "Foreign", 450, 0))
                .procesoLlamado = 98L;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(2, 0, 0));
        ArgumentCaptor<Actividad> guardadas = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(Actividad::getBpmnId, Actividad::getProcesoLlamado)
                .containsExactly(tuple("Activity_Facturar", facturacion), tuple("Activity_Ajena", null));
    }

    @Test
    @DisplayName("Una actividad cambia por su tipo, por su descripcion o por el proceso que llama, y cada una es una edicion")
    void actividades_cambianCadaUnaPorUnaCosa() {
        preparar();
        Proceso facturacion = Proceso.builder().id(99L).empresa(tienda).nombre("Invoicing").build();
        given(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(99L, EMPRESA_ID)).willReturn(Optional.of(facturacion));
        planNodos.get(COBRAR).tipoActividad = TipoActividad.ENVIO;
        planNodos.get(EMPACAR).descripcion = "Wrap with care";
        planNodos.get(VERIFICAR).procesoLlamado = 99L;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 3, 0));
        assertThat(cobrar.getTipoActividad()).isEqualTo(TipoActividad.ENVIO);
        assertThat(cobrar.getDescripcion()).isEqualTo("Charges the card");
        assertThat(empacar.getDescripcion()).isEqualTo("Wrap with care");
        assertThat(empacar.getTipoActividad()).isEqualTo(TipoActividad.USUARIO);
        assertThat(verificar.getProcesoLlamado()).isSameAs(facturacion);
        verify(actividadRepository, never()).save(any());
    }

    @Test
    @DisplayName("PR 69: un formulario que llega, cambia o se va es una edicion; el mismo de antes no")
    void formularios_cadaCambioEsUnaEdicion() {
        preparar();
        Formulario antes = CampoArmado.formulario(CampoArmado.campo("cajas", TipoDeCampo.NUMERO));
        Formulario despues = CampoArmado.formulario(CampoArmado.campo("cajas", TipoDeCampo.NUMERO).requerido());
        empacar.setFormulario(antes);
        inicio.setFormulario(antes);
        planNodos.get(EMPACAR).formulario = despues;
        planNodos.get(INICIO).formulario = antes;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getFormulario()).isEqualTo(despues);
        assertThat(inicio.getFormulario()).isEqualTo(antes);
    }

    @Test
    @DisplayName("PR 69: un archivo sin el formulario se lo quita al nodo")
    void formularios_unArchivoSinElLoQuita() {
        preparar();
        empacar.setFormulario(CampoArmado.formulario(CampoArmado.campo("cajas", TipoDeCampo.NUMERO)));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getFormulario()).isNull();
    }

    @Test
    @DisplayName("PR 69: una tarea y un evento nuevos se crean con el formulario del archivo")
    void formularios_nodosNuevos() {
        preparar();
        guardaConId(actividadRepository, Actividad.class, Actividad::setId);
        guardaConId(eventoRepository, Evento.class, Evento::setId);
        Formulario formulario = CampoArmado.formulario(CampoArmado.campo("motivo", TipoDeCampo.TEXTO));
        nodoPlan(NodoPlan.actividad("Activity_Aprobar", VENTAS, TipoActividad.USUARIO, "Approve", 150, 60))
                .formulario = formulario;
        nodoPlan(NodoPlan.evento("Event_AMano", VENTAS, TipoEvento.INICIO, "By hand", 20, 7)).formulario = formulario;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(2, 0, 0));
        ArgumentCaptor<Actividad> actividad = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository).save(actividad.capture());
        assertThat(actividad.getValue().getFormulario()).isEqualTo(formulario);
        ArgumentCaptor<Evento> evento = ArgumentCaptor.forClass(Evento.class);
        verify(eventoRepository).save(evento.capture());
        assertThat(evento.getValue().getFormulario()).isEqualTo(formulario);
    }

    @Test
    @DisplayName("PR 70: una asignacion que llega, cambia de persona, de tipo o de variable, o se va, es una edicion")
    void asignaciones_cadaCambioEsUnaEdicion() {
        preparar();
        empacar.setTipoAsignacion(TipoAsignacion.PERSONA);
        empacar.setAsignacionUsuarioId(5L);
        planNodos.get(EMPACAR).asignacion = new AsignacionResponse(TipoAsignacion.PERSONA, 5L, null);
        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 0));

        planNodos.get(EMPACAR).asignacion = new AsignacionResponse(TipoAsignacion.PERSONA, 6L, null);
        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getAsignacionUsuarioId()).isEqualTo(6L);

        planNodos.get(EMPACAR).asignacion = new AsignacionResponse(TipoAsignacion.CAMPO, null, "aprobador");
        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getTipoAsignacion()).isEqualTo(TipoAsignacion.CAMPO);
        assertThat(empacar.getAsignacionUsuarioId()).isNull();
        assertThat(empacar.getAsignacionCampo()).isEqualTo("aprobador");

        planNodos.get(EMPACAR).asignacion = new AsignacionResponse(TipoAsignacion.CAMPO, null, "revisor");
        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getAsignacionCampo()).isEqualTo("revisor");

        planNodos.get(EMPACAR).asignacion = new AsignacionResponse(TipoAsignacion.INICIADOR, null, null);
        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getTipoAsignacion()).isEqualTo(TipoAsignacion.INICIADOR);
        assertThat(empacar.getAsignacionCampo()).isNull();

        planNodos.get(EMPACAR).asignacion = null;
        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getTipoAsignacion()).isNull();
    }

    @Test
    @DisplayName("PR 70: una tarea nueva se crea con la asignacion del archivo")
    void asignaciones_tareaNueva() {
        preparar();
        guardaConId(actividadRepository, Actividad.class, Actividad::setId);
        nodoPlan(NodoPlan.actividad("Activity_Aprobar", VENTAS, TipoActividad.USUARIO, "Approve", 150, 60))
                .asignacion = new AsignacionResponse(TipoAsignacion.PERSONA, 5L, null);
        nodoPlan(NodoPlan.actividad("Activity_Pagar", VENTAS, TipoActividad.USUARIO, "Pay", 300, 60))
                .asignacion = new AsignacionResponse(TipoAsignacion.CAMPO, null, "aprobador");

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(2, 0, 0));
        ArgumentCaptor<Actividad> actividades = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository, times(2)).save(actividades.capture());
        assertThat(actividades.getAllValues()).extracting(Actividad::getTipoAsignacion,
                Actividad::getAsignacionUsuarioId, Actividad::getAsignacionCampo).containsExactly(
                        tuple(TipoAsignacion.PERSONA, 5L, null),
                        tuple(TipoAsignacion.CAMPO, null, "aprobador"));
    }

    @Test
    @DisplayName("Un gateway que cambia de tipo es una edicion")
    void gateway_cambiaDeTipo() {
        preparar();
        planNodos.get(APROBADO).tipoGateway = TipoGateway.INCLUSIVO;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(aprobado.getTipoGateway()).isEqualTo(TipoGateway.INCLUSIVO);
    }

    @Test
    @DisplayName("Un evento toma del archivo su tipo y su temporizador; el de borde sigue donde se dibuja")
    void eventos_cambianTipoYTemporizador() {
        preparar();
        planNodos.get(INICIO).tipoEvento = TipoEvento.MENSAJE_INICIO;
        NodoPlan elPlazo = planNodos.get(PLAZO);
        elPlazo.tipoTemporizador = TipoTemporizador.FECHA;
        elPlazo.temporizador = "2026-12-24T00:00:00";
        elPlazo.interrumpe = false;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 2, 0));
        assertThat(inicio.getTipoEvento()).isEqualTo(TipoEvento.MENSAJE_INICIO);
        assertThat(plazo.getTipoEvento()).isEqualTo(TipoEvento.TEMPORIZADOR_BORDE);
        assertThat(plazo.getTipoTemporizador()).isEqualTo(TipoTemporizador.FECHA);
        assertThat(plazo.getTemporizador()).isEqualTo("2026-12-24T00:00:00");
        assertThat(plazo.getInterrumpe()).isFalse();
        assertThat(plazo.getPosicionX()).isEqualTo(230);
        assertThat(plazo.getPosicionY()).isEqualTo(30);
    }

    @Test
    @DisplayName("Un nodo que cambia de lane, de sitio y de nombre se edita una vez, en el marco de su nueva lane")
    void nodoMovido_aOtraLane() {
        preparar();
        NodoPlan elEmpacar = planNodos.get(EMPACAR);
        elEmpacar.lane = VENTAS;
        elEmpacar.nombre = "Pack and seal";
        elEmpacar.x = 120;
        elEmpacar.y = 30;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(empacar.getLane()).isSameAs(laneVentas);
        assertThat(empacar.getNombre()).isEqualTo("Pack and seal");
        assertThat(empacar.getPosicionX()).isEqualTo(220);
        assertThat(empacar.getPosicionY()).isEqualTo(70);
    }

    @Test
    @DisplayName("Un bpmn_id que cambia de clase es otro nodo: se da de baja y lo que lo nombraba pasa al nuevo")
    void nodoQueCambiaDeClase_esOtroNodo() {
        preparar();
        guardaConId(gatewayRepository, Gateway.class, Gateway::setId);
        NodoPlan elFin = planNodos.get(FIN);
        elFin.clase = PlanBpmn.Clase.GATEWAY;
        elFin.tipoEvento = null;
        elFin.tipoGateway = TipoGateway.EXCLUSIVO;

        // El nodo nuevo, y los dos flujos y el mensaje que llegaban o salian del viejo
        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 3, 1));
        verify(eventoRepository).delete(fin);
        ArgumentCaptor<Gateway> nuevo = ArgumentCaptor.forClass(Gateway.class);
        verify(gatewayRepository).save(nuevo.capture());
        Gateway elNuevo = nuevo.getValue();
        assertThat(elNuevo.getBpmnId()).isEqualTo(FIN);
        assertThat(elNuevo.getPosicionX()).isEqualTo(550);
        assertThat(elNuevo.getPosicionY()).isEqualTo(310);
        assertThat(flujo4.getDestino()).isSameAs(elNuevo);
        assertThat(flujo5.getDestino()).isSameAs(elNuevo);
        assertThat(confirmacion.getNodoOrigen()).isSameAs(elNuevo);
    }

    @Test
    @DisplayName("Los nodos que el archivo no tiene se dan de baja, cada uno en el repositorio de su tipo")
    void nodosQueSobran_seDanDeBajaPorSuTipo() {
        Actividad vieja = actividad(81L, "Activity_Vieja", "Old task", TipoActividad.USUARIO, laneAlmacen, 600, 330);
        Gateway viejo = gateway(82L, "Gateway_Viejo", "Old gateway", TipoGateway.EXCLUSIVO, laneAlmacen, 650, 330);
        Evento eventoViejo = evento(83L, "Event_Viejo", "Old event", TipoEvento.FIN, laneAlmacen, 700, 330);
        preparar();

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 3));
        verify(actividadRepository).delete(vieja);
        verify(gatewayRepository).delete(viejo);
        verify(eventoRepository).delete(eventoViejo);
        verifyNoInteractions(configuracionTiendaService);
    }

    @Test
    @DisplayName("Un nodo que entra a un subproceso y otro que sale de el son dos ediciones")
    void nodosQueEntranYSalenDeUnSubproceso() {
        preparar();
        planNodos.get(EMPACAR).padre = REVISAR;
        planNodos.get(VERIFICAR).padre = null;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 2, 0));
        assertThat(empacar.getPadre()).isSameAs(revisar);
        assertThat(verificar.getPadre()).isNull();
    }

    @Test
    @DisplayName("Lo nuevo se pega a su subproceso y a su actividad sin contar como edicion")
    void nodosNuevos_sePeganSinContarComoEdicion() {
        preparar();
        guardaConId(actividadRepository, Actividad.class, Actividad::setId);
        guardaConId(eventoRepository, Evento.class, Evento::setId);
        nodoPlan(NodoPlan.actividad("Activity_Devolucion", ALMACEN, TipoActividad.SUBPROCESO, "Return", 500, 50));
        nodoPlan(NodoPlan.actividad("Activity_Inspeccionar", ALMACEN, TipoActividad.USUARIO, "Inspect", 520, 60))
                .padre = "Activity_Devolucion";
        NodoPlan limite = nodoPlan(NodoPlan.evento("Event_Limite", ALMACEN, TipoEvento.TEMPORIZADOR_BORDE,
                "Two days", 540, 90));
        limite.adjuntoA = "Activity_Devolucion";
        limite.tipoTemporizador = TipoTemporizador.DURACION;
        limite.temporizador = "P2D";
        limite.interrumpe = true;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(3, 0, 0));
        ArgumentCaptor<Actividad> actividad = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository, times(2)).save(actividad.capture());
        Actividad devolucion = actividad.getAllValues().get(0);
        assertThat(actividad.getAllValues().get(1).getPadre()).isSameAs(devolucion);
        ArgumentCaptor<Evento> evento = ArgumentCaptor.forClass(Evento.class);
        verify(eventoRepository).save(evento.capture());
        assertThat(evento.getValue().getAdjuntoA()).isSameAs(devolucion);
    }

    @Test
    @DisplayName("Un evento de borde que pasa a otra actividad es una edicion")
    void eventoDeBorde_cambiaDeActividad() {
        preparar();
        planNodos.get(PLAZO).adjuntoA = APROBADO;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(plazo.getAdjuntoA()).isSameAs(aprobado);
    }

    // ---------- Flujos ----------

    @Test
    @DisplayName("Un flujo nuevo se crea entre sus nodos, en su pool y con su condicion")
    void arcoNuevo_seCreaEntreSusNodos() {
        preparar();
        guardaConId(arcoRepository, Arco.class, Arco::setId);
        planArcos.add(new PlanBpmn.Arco("Flow_Nuevo", TIENDA_EN_ARCHIVO, COBRAR, FIN, "Cancel", "payment.failed",
                true, 5));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 0, 0));
        ArgumentCaptor<Arco> guardado = ArgumentCaptor.forClass(Arco.class);
        verify(arcoRepository).save(guardado.capture());
        Arco nuevo = guardado.getValue();
        assertThat(nuevo.getEmpresa()).isSameAs(tienda);
        assertThat(nuevo.getBpmnId()).isEqualTo("Flow_Nuevo");
        assertThat(nuevo.getEtiqueta()).isEqualTo("Cancel");
        assertThat(nuevo.getCondicion()).isEqualTo("payment.failed");
        assertThat(nuevo.isPorDefecto()).isTrue();
        assertThat(nuevo.getOrden()).isEqualTo(5);
        assertThat(nuevo.getOrigen()).isSameAs(cobrar);
        assertThat(nuevo.getDestino()).isSameAs(fin);
        assertThat(nuevo.getPool()).isSameAs(poolTienda);
    }

    @Test
    @DisplayName("Un flujo que cambia toma del archivo todo lo suyo y es una edicion")
    void arcoEditado_tomaLoDelArchivo() {
        preparar();
        cambiar(planArcos, PlanBpmn.Arco::bpmnId, new PlanBpmn.Arco("Flow_3", CLIENTE, COBRAR, EMPACAR, "No",
                "payment.failed", true, 9));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(flujo3.getEtiqueta()).isEqualTo("No");
        assertThat(flujo3.getCondicion()).isEqualTo("payment.failed");
        assertThat(flujo3.isPorDefecto()).isTrue();
        assertThat(flujo3.getOrden()).isEqualTo(9);
        assertThat(flujo3.getOrigen()).isSameAs(cobrar);
        assertThat(flujo3.getDestino()).isSameAs(empacar);
        assertThat(flujo3.getPool()).isSameAs(poolCliente);
    }

    @Test
    @DisplayName("Un flujo que el archivo no tiene se da de baja")
    void arcoQueSobra_seDaDeBaja() {
        preparar();
        quitar(planArcos, PlanBpmn.Arco::bpmnId, "Flow_5");

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 1));
        verify(arcoRepository).delete(flujo5);
    }

    // ---------- Mensajes ----------

    @Test
    @DisplayName("Un mensaje nuevo se crea con lo del archivo, su respuesta y su correlacion, sin contar ediciones")
    void mensajeNuevo_conRespuestaYCorrelacion() {
        preparar();
        guardaConId(mensajeRepository, Mensaje.class, Mensaje::setId);
        given(correlacionRepository.save(any(Correlacion.class))).willAnswer(llamada -> llamada.getArgument(0));
        MensajePlan reembolso = mensajePlan(new MensajePlan("MessageFlow_Reembolso", "Refund request", CLIENTE,
                TIENDA_EN_ARCHIVO));
        reembolso.contenido = "Order and amount";
        reembolso.nodoDestino = COBRAR;
        reembolso.tipoDestino = TipoDestino.COLA;
        reembolso.siFalla = AccionSiFalla.FINALIZAR;
        reembolso.manejoError = APROBADO;
        reembolso.origenExterno = true;
        reembolso.campos = List.of(new CampoDeMensaje("amount", TipoDeDato.NUMERO));
        reembolso.usoDeLosDatos = "Refunds";
        reembolso.variable = "refund";
        reembolso.respuesta = AVISO;
        reembolso.correlacion = new PlanBpmn.Correlacion("order.id", "orderId", PoliticaSinCaso.INICIAR_CASO);

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(2, 0, 0));
        ArgumentCaptor<Mensaje> guardado = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajeRepository).save(guardado.capture());
        Mensaje nuevo = guardado.getValue();
        assertThat(nuevo.getEmpresa()).isSameAs(tienda);
        assertThat(nuevo.getProceso()).isSameAs(proceso);
        assertThat(nuevo.getBpmnId()).isEqualTo("MessageFlow_Reembolso");
        assertThat(nuevo.getNombre()).isEqualTo("Refund request");
        assertThat(nuevo.getContenido()).isEqualTo("Order and amount");
        assertThat(nuevo.getPoolOrigen()).isSameAs(poolCliente);
        assertThat(nuevo.getPoolDestino()).isSameAs(poolTienda);
        assertThat(nuevo.getNodoOrigen()).isNull();
        assertThat(nuevo.getNodoDestino()).isSameAs(cobrar);
        assertThat(nuevo.getTipoDestino()).isEqualTo(TipoDestino.COLA);
        assertThat(nuevo.getSiFalla()).isEqualTo(AccionSiFalla.FINALIZAR);
        assertThat(nuevo.getNodoManejoError()).isSameAs(aprobado);
        assertThat(nuevo.isOrigenExterno()).isTrue();
        assertThat(nuevo.getCampos()).containsExactly(new CampoDeMensaje("amount", TipoDeDato.NUMERO));
        assertThat(nuevo.getUsoDeLosDatos()).isEqualTo("Refunds");
        assertThat(nuevo.getVariable()).isEqualTo("refund");
        assertThat(nuevo.getRespuestaEsperada()).isSameAs(aviso);
        ArgumentCaptor<Correlacion> correlacion = ArgumentCaptor.forClass(Correlacion.class);
        verify(correlacionRepository).save(correlacion.capture());
        Correlacion suya = correlacion.getValue();
        assertThat(suya.getEmpresa()).isSameAs(tienda);
        assertThat(suya.getMensaje()).isSameAs(nuevo);
        assertThat(suya.getCriterio()).isEqualTo("order.id");
        assertThat(suya.getCampo()).isEqualTo("orderId");
        assertThat(suya.getSinCaso()).isEqualTo(PoliticaSinCaso.INICIAR_CASO);
    }

    @Test
    @DisplayName("Un mensaje que cambia sin cambiar de pools toma del archivo todo lo suyo y es una edicion")
    void mensajeEditado_tomaLoDelArchivo() {
        preparar();
        MensajePlan elAviso = planMensajes.get(AVISO);
        elAviso.nombre = "Payment receipt";
        elAviso.contenido = "Amount paid";
        elAviso.nodoOrigen = APROBADO;
        elAviso.nodoDestino = null;
        elAviso.tipoDestino = TipoDestino.COLA;
        elAviso.siFalla = AccionSiFalla.MANEJAR_ERROR;
        elAviso.manejoError = FIN;
        elAviso.origenExterno = true;
        elAviso.campos = List.of(new CampoDeMensaje("amount", TipoDeDato.NUMERO));
        elAviso.usoDeLosDatos = "Accounting";
        elAviso.variable = "receipt";

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(aviso.getNombre()).isEqualTo("Payment receipt");
        assertThat(aviso.getContenido()).isEqualTo("Amount paid");
        assertThat(aviso.getNodoOrigen()).isSameAs(aprobado);
        assertThat(aviso.getNodoDestino()).isNull();
        assertThat(aviso.getTipoDestino()).isEqualTo(TipoDestino.COLA);
        assertThat(aviso.getSiFalla()).isEqualTo(AccionSiFalla.MANEJAR_ERROR);
        assertThat(aviso.getNodoManejoError()).isSameAs(fin);
        assertThat(aviso.isOrigenExterno()).isTrue();
        assertThat(aviso.getCampos()).containsExactly(new CampoDeMensaje("amount", TipoDeDato.NUMERO));
        assertThat(aviso.getUsoDeLosDatos()).isEqualTo("Accounting");
        assertThat(aviso.getVariable()).isEqualTo("receipt");
        assertThat(aviso.getPoolOrigen()).isSameAs(poolTienda);
        verify(mensajeRepository, never()).save(any());
        verify(mensajeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Un mensaje que cambia de pools es otro mensaje: el viejo se da de baja y se crea el nuevo")
    void mensajeQueCambiaDePools_esOtroMensaje() {
        preparar();
        guardaConId(mensajeRepository, Mensaje.class, Mensaje::setId);
        MensajePlan elAviso = planMensajes.get(AVISO);
        elAviso.poolOrigen = CLIENTE;
        elAviso.poolDestino = TIENDA_EN_ARCHIVO;
        elAviso.nodoOrigen = null;
        elAviso.nodoDestino = COBRAR;

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 0, 1));
        verify(mensajeRepository).delete(aviso);
        ArgumentCaptor<Mensaje> guardado = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajeRepository).save(guardado.capture());
        assertThat(guardado.getValue().getBpmnId()).isEqualTo(AVISO);
        assertThat(guardado.getValue().getPoolOrigen()).isSameAs(poolCliente);
        assertThat(guardado.getValue().getPoolDestino()).isSameAs(poolTienda);
    }

    @Test
    @DisplayName("Un mensaje que el archivo no tiene se va con su correlacion, y quien esperaba su respuesta ya no")
    void mensajeQueSobra_seLlevaSuCorrelacionYLaRespuesta() {
        preparar();
        planMensajes.remove(CONFIRMACION);
        planMensajes.get(PEDIDO).respuesta = null;

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 1));
        verify(correlacionRepository).delete(correlacionConfirmacion);
        verify(mensajeRepository).delete(confirmacion);
        assertThat(pedido.getRespuestaEsperada()).isNull();
    }

    @Test
    @DisplayName("Dar de baja un mensaje no toca las respuestas que esperan los demas")
    void mensajeQueSobra_noTocaLasRespuestasDeLosDemas() {
        preparar();
        planMensajes.remove(AVISO);

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 1));
        verify(mensajeRepository).delete(aviso);
        verify(correlacionRepository, never()).delete(any());
        assertThat(pedido.getRespuestaEsperada()).isSameAs(confirmacion);
    }

    @Test
    @DisplayName("Un mensaje que pasa a esperar una respuesta es una edicion")
    void respuestaNueva_esUnaEdicion() {
        preparar();
        planMensajes.get(AVISO).respuesta = PEDIDO;

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(aviso.getRespuestaEsperada()).isSameAs(pedido);
    }

    @Test
    @DisplayName("Una correlacion nueva se crea, una que cambia se edita y una que el archivo no tiene se da de baja")
    void correlaciones_nuevaEditadaYQuitada() {
        preparar();
        given(correlacionRepository.save(any(Correlacion.class))).willAnswer(llamada -> llamada.getArgument(0));
        planMensajes.get(AVISO).correlacion = new PlanBpmn.Correlacion("payment.id", "paymentId",
                PoliticaSinCaso.DESCARTAR);
        planMensajes.get(PEDIDO).correlacion = new PlanBpmn.Correlacion("order.number", "number",
                PoliticaSinCaso.DESCARTAR);
        planMensajes.get(CONFIRMACION).correlacion = null;

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 1, 1));
        ArgumentCaptor<Correlacion> guardada = ArgumentCaptor.forClass(Correlacion.class);
        verify(correlacionRepository).save(guardada.capture());
        assertThat(guardada.getValue().getEmpresa()).isSameAs(tienda);
        assertThat(guardada.getValue().getMensaje()).isSameAs(aviso);
        assertThat(guardada.getValue().getCriterio()).isEqualTo("payment.id");
        assertThat(guardada.getValue().getCampo()).isEqualTo("paymentId");
        assertThat(guardada.getValue().getSinCaso()).isEqualTo(PoliticaSinCaso.DESCARTAR);
        assertThat(correlacionPedido.getCriterio()).isEqualTo("order.number");
        assertThat(correlacionPedido.getCampo()).isEqualTo("number");
        assertThat(correlacionPedido.getSinCaso()).isEqualTo(PoliticaSinCaso.DESCARTAR);
        verify(correlacionRepository).delete(correlacionConfirmacion);
        verify(mensajeRepository, never()).delete(any());
    }

    // ---------- Lo descriptivo ----------

    @Test
    @DisplayName("Un artefacto nuevo en una lane usa el marco de ella; uno suelto, el de los demas sueltos")
    void artefactosNuevos_enLaneYSueltos() {
        preparar();
        guardaConId(artefactoRepository, Artefacto.class, Artefacto::setId);
        planArtefactos.add(new PlanBpmn.Artefacto("TextAnnotation_Almacen", TipoArtefacto.ANOTACION, "Fragile",
                TIENDA_EN_ARCHIVO, ALMACEN, 10, 5, 100, 30));
        planArtefactos.add(new PlanBpmn.Artefacto("DataObjectReference_Factura", TipoArtefacto.OBJETO_DATOS,
                "Invoice", null, null, 30, 10, 36, 50));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(2, 0, 0));
        ArgumentCaptor<Artefacto> guardados = ArgumentCaptor.forClass(Artefacto.class);
        verify(artefactoRepository, times(2)).save(guardados.capture());
        Artefacto enLane = guardados.getAllValues().get(0);
        assertThat(enLane.getEmpresa()).isSameAs(tienda);
        assertThat(enLane.getProceso()).isSameAs(proceso);
        assertThat(enLane.getBpmnId()).isEqualTo("TextAnnotation_Almacen");
        assertThat(enLane.getTipo()).isEqualTo(TipoArtefacto.ANOTACION);
        assertThat(enLane.getTexto()).isEqualTo("Fragile");
        assertThat(enLane.getPool()).isSameAs(poolTienda);
        assertThat(enLane.getLane()).isSameAs(laneAlmacen);
        assertThat(enLane.getPosicionX()).isEqualTo(110);
        assertThat(enLane.getPosicionY()).isEqualTo(305);
        assertThat(enLane.getAncho()).isEqualTo(100);
        assertThat(enLane.getAlto()).isEqualTo(30);
        Artefacto suelto = guardados.getAllValues().get(1);
        assertThat(suelto.getTipo()).isEqualTo(TipoArtefacto.OBJETO_DATOS);
        assertThat(suelto.getPool()).isNull();
        assertThat(suelto.getLane()).isNull();
        assertThat(suelto.getPosicionX()).isEqualTo(50);
        assertThat(suelto.getPosicionY()).isEqualTo(510);
        assertThat(suelto.getAncho()).isEqualTo(36);
        assertThat(suelto.getAlto()).isEqualTo(50);
    }

    @Test
    @DisplayName("Un artefacto que cambia toma del archivo todo lo suyo y es una edicion")
    void artefactoEditado_tomaLoDelArchivo() {
        preparar();
        cambiar(planArtefactos, PlanBpmn.Artefacto::bpmnId, new PlanBpmn.Artefacto(NOTA, TipoArtefacto.OBJETO_DATOS,
                "Invoice", CLIENTE, null, 5, 6, 36, 50));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 1, 0));
        assertThat(nota.getTipo()).isEqualTo(TipoArtefacto.OBJETO_DATOS);
        assertThat(nota.getTexto()).isEqualTo("Invoice");
        assertThat(nota.getPool()).isSameAs(poolCliente);
        assertThat(nota.getLane()).isNull();
        assertThat(nota.getPosicionX()).isEqualTo(25);
        assertThat(nota.getPosicionY()).isEqualTo(506);
        assertThat(nota.getAncho()).isEqualTo(36);
        assertThat(nota.getAlto()).isEqualTo(50);
        assertThat(grupo.getPosicionX()).isEqualTo(20);
    }

    @Test
    @DisplayName("Un artefacto y una asociacion que el archivo no tiene se dan de baja")
    void artefactoYAsociacionQueSobran_seDanDeBaja() {
        preparar();
        quitar(planArtefactos, PlanBpmn.Artefacto::bpmnId, GRUPO);
        quitar(planAsociaciones, PlanBpmn.Asociacion::bpmnId, ASOCIACION);

        assertThat(aplicar(RolAcceso.ADMINISTRADOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 2));
        verify(artefactoRepository).delete(grupo);
        verify(asociacionRepository).delete(asociacion);
    }

    @Test
    @DisplayName("Una asociacion nueva se crea con sus extremos y una que cambia toma los del archivo")
    void asociaciones_nuevaYEditada() {
        preparar();
        guardaConId(asociacionRepository, Asociacion.class, Asociacion::setId);
        planAsociaciones.add(new PlanBpmn.Asociacion("DataInputAssociation_1", TipoAsociacion.ENTRADA_DATOS, GRUPO,
                EMPACAR, DireccionAsociacion.UNA));
        cambiar(planAsociaciones, PlanBpmn.Asociacion::bpmnId, new PlanBpmn.Asociacion(ASOCIACION,
                TipoAsociacion.SALIDA_DATOS, COBRAR, NOTA, DireccionAsociacion.AMBAS));

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(1, 1, 0));
        ArgumentCaptor<Asociacion> guardada = ArgumentCaptor.forClass(Asociacion.class);
        verify(asociacionRepository).save(guardada.capture());
        Asociacion nueva = guardada.getValue();
        assertThat(nueva.getEmpresa()).isSameAs(tienda);
        assertThat(nueva.getProceso()).isSameAs(proceso);
        assertThat(nueva.getBpmnId()).isEqualTo("DataInputAssociation_1");
        assertThat(nueva.getTipo()).isEqualTo(TipoAsociacion.ENTRADA_DATOS);
        assertThat(nueva.getOrigen()).isEqualTo(GRUPO);
        assertThat(nueva.getDestino()).isEqualTo(EMPACAR);
        assertThat(nueva.getDireccion()).isEqualTo(DireccionAsociacion.UNA);
        assertThat(asociacion.getTipo()).isEqualTo(TipoAsociacion.SALIDA_DATOS);
        assertThat(asociacion.getOrigen()).isEqualTo(COBRAR);
        assertThat(asociacion.getDestino()).isEqualTo(NOTA);
        assertThat(asociacion.getDireccion()).isEqualTo(DireccionAsociacion.AMBAS);
    }

    // ---------- Extensiones ----------

    @Test
    @DisplayName("Lo ajeno del archivo reemplaza lo del borrador, y lo del pool de la organizacion va con su id de siempre")
    void extensiones_reemplazanLasDelBorrador() {
        preparar();
        extensiones.put(TIENDA_EN_ARCHIVO, "<a/>");
        extensiones.put(TIENDA_EN_ARCHIVO + "#process", "<b/>");
        extensiones.put(COBRAR, "<c/>");
        extensiones.put(ExtensionXml.DEFINICIONES, "<d/>");

        assertThat(aplicar(RolAcceso.EDITOR)).isEqualTo(new AplicacionDelModelo.Cambios(0, 0, 0));
        InOrder orden = inOrder(extensionXmlRepository);
        orden.verify(extensionXmlRepository).borrarDelProceso(PROCESO_ID, EMPRESA_ID);
        orden.verify(extensionXmlRepository).saveAll(extensionesGuardadas.capture());
        assertThat(extensionesGuardadas.getValue())
                .extracting(ExtensionXml::getElemento, ExtensionXml::getXml, ExtensionXml::getProcesoId,
                        ExtensionXml::getEmpresa)
                .containsExactlyInAnyOrder(tuple(TIENDA, "<a/>", PROCESO_ID, tienda),
                        tuple(TIENDA + "#process", "<b/>", PROCESO_ID, tienda),
                        tuple(COBRAR, "<c/>", PROCESO_ID, tienda),
                        tuple(ExtensionXml.DEFINICIONES, "<d/>", PROCESO_ID, tienda));
    }

    @Test
    @DisplayName("Un archivo sin nada ajeno deja el borrador sin extensiones")
    void sinExtensiones_borraLasDelBorrador() {
        preparar();

        aplicar(RolAcceso.EDITOR);

        verify(extensionXmlRepository).borrarDelProceso(PROCESO_ID, EMPRESA_ID);
        verify(extensionXmlRepository).saveAll(extensionesGuardadas.capture());
        assertThat(extensionesGuardadas.getValue()).isEmpty();
    }

    // ---------- Ayudas ----------

    /** Cada cosa que lee la aplicacion, mas los roles de la tienda y las correlaciones de sus mensajes. */
    private void preparar() {
        borrador();
        rolesDeLaTienda();
        given(correlacionRepository.findByMensajeIdAndEmpresaId(anyLong(), eq(EMPRESA_ID)))
                .willAnswer(llamada -> Optional.ofNullable(correlaciones.get(llamada.<Long>getArgument(0))));
    }

    /** Tres nodos nuevos en el archivo: una actividad en Ventas, y un gateway y un evento en Almacen. */
    private void tresNodosNuevos() {
        preparar();
        guardaConId(actividadRepository, Actividad.class, Actividad::setId);
        guardaConId(gatewayRepository, Gateway.class, Gateway::setId);
        guardaConId(eventoRepository, Evento.class, Evento::setId);
        nodoPlan(NodoPlan.actividad("Activity_Avisar", VENTAS, TipoActividad.ENVIO, "Notify customer", 150, 60))
                .descripcion = "Sends an email";
        nodoPlan(NodoPlan.gateway("Gateway_Juntar", ALMACEN, TipoGateway.PARALELO, "Join", 10, 5));
        NodoPlan espera = nodoPlan(NodoPlan.evento("Event_Espera", ALMACEN, TipoEvento.TEMPORIZADOR_INTERMEDIO,
                "Wait a day", 20, 7));
        espera.tipoTemporizador = TipoTemporizador.DURACION;
        espera.temporizador = "P1D";
        espera.interrumpe = false;
    }

    /** El borrador tal como lo devuelven los repositorios. */
    private void borrador() {
        given(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(PROCESO_ID, EMPRESA_ID)).willReturn(pools);
        given(laneRepository.delProcesoEnOrden(PROCESO_ID, EMPRESA_ID)).willReturn(lanes);
        given(actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID))
                .willReturn(actividades);
        given(gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID))
                .willReturn(gateways);
        given(eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID))
                .willReturn(eventos);
        given(arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID)).willReturn(arcos);
        given(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID))
                .willReturn(mensajes);
        given(artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID))
                .willReturn(artefactos);
        given(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO_ID, EMPRESA_ID))
                .willReturn(asociaciones);
    }

    private void rolesDeLaTienda() {
        given(rolProcesoRepository.findFirstByEmpresaIdAndNombreIgnoreCaseAndActivoTrueOrderByIdAsc(eq(EMPRESA_ID),
                anyString())).willAnswer(llamada -> Optional.ofNullable(roles.get(llamada.<String>getArgument(1)
                        .toLowerCase(Locale.ROOT))));
    }

    /** Guardar asigna un id, como la base de datos: lo nuevo se reconoce despues por el suyo. */
    private <T> void guardaConId(CrudRepository<T, Long> repositorio, Class<T> tipo, BiConsumer<T, Long> asignar) {
        Answer<T> conId = llamada -> {
            T entidad = llamada.getArgument(0);
            asignar.accept(entidad, siguienteId.incrementAndGet());
            return entidad;
        };
        given(repositorio.save(any(tipo))).willAnswer(conId);
    }

    private AplicacionDelModelo.Cambios aplicar(RolAcceso rol) {
        PlanBpmn plan = new PlanBpmn("Order fulfillment", null, null, List.copyOf(planPools), List.copyOf(planLanes),
                planNodos.values().stream().map(NodoPlan::armar).toList(), List.copyOf(planArcos),
                planMensajes.values().stream().map(MensajePlan::armar).toList(), new LinkedHashMap<>(extensiones),
                new InformeBpmn(Map.of(), List.of(), List.of(), List.of(), List.of()), modo,
                List.copyOf(planArtefactos), List.copyOf(planAsociaciones));
        return aplicacion.aplicar(EMPRESA_ID, USUARIO_ID, rol, proceso, plan);
    }

    private void nadaSeGuardoNiSeBorro() {
        verify(poolRepository, never()).save(any());
        verify(poolRepository, never()).delete(any());
        verify(laneRepository, never()).save(any());
        verify(laneRepository, never()).delete(any());
        verify(actividadRepository, never()).save(any());
        verify(actividadRepository, never()).delete(any());
        verify(gatewayRepository, never()).save(any());
        verify(gatewayRepository, never()).delete(any());
        verify(eventoRepository, never()).save(any());
        verify(eventoRepository, never()).delete(any());
        verify(arcoRepository, never()).save(any());
        verify(arcoRepository, never()).delete(any());
        verify(mensajeRepository, never()).save(any());
        verify(mensajeRepository, never()).delete(any());
        verify(correlacionRepository, never()).save(any());
        verify(correlacionRepository, never()).delete(any());
        verify(artefactoRepository, never()).save(any());
        verify(artefactoRepository, never()).delete(any());
        verify(asociacionRepository, never()).save(any());
        verify(asociacionRepository, never()).delete(any());
    }

    private static <T> void cambiar(List<T> lista, Function<T, String> bpmnId, T nuevo) {
        for (int i = 0; i < lista.size(); i++) {
            if (bpmnId.apply(lista.get(i)).equals(bpmnId.apply(nuevo))) {
                lista.set(i, nuevo);
                return;
            }
        }
        throw new IllegalArgumentException("El plan no tiene " + bpmnId.apply(nuevo));
    }

    private static <T> void quitar(List<T> lista, Function<T, String> bpmnId, String id) {
        assertThat(lista.removeIf(uno -> bpmnId.apply(uno).equals(id))).isTrue();
    }

    private NodoPlan nodoPlan(NodoPlan nodo) {
        planNodos.put(nodo.bpmnId, nodo);
        return nodo;
    }

    private MensajePlan mensajePlan(MensajePlan mensaje) {
        planMensajes.put(mensaje.bpmnId, mensaje);
        return mensaje;
    }

    private RolProceso rol(Long id, String nombre) {
        RolProceso rol = RolProceso.builder().id(id).empresa(tienda).nombre(nombre).build();
        roles.put(nombre.toLowerCase(Locale.ROOT), rol);
        return rol;
    }

    private Pool pool(Long id, String bpmnId, String nombre, TipoParticipante tipo, Integracion integracion, int orden,
            boolean cajaNegra) {
        Pool pool = Pool.builder().id(id).empresa(tienda).proceso(proceso).bpmnId(bpmnId).nombre(nombre)
                .tipoParticipante(tipo).integracion(integracion).orden(orden).cajaNegra(cajaNegra).build();
        pools.add(pool);
        return pool;
    }

    private Lane lane(Long id, String bpmnId, String nombre, int orden, Pool pool, RolProceso rol) {
        Lane lane = Lane.builder().id(id).empresa(tienda).bpmnId(bpmnId).nombre(nombre).orden(orden).pool(pool)
                .rolProceso(rol).build();
        lanes.add(lane);
        return lane;
    }

    private Actividad actividad(Long id, String bpmnId, String nombre, TipoActividad tipo, Lane lane, int x, int y) {
        Actividad actividad = Actividad.builder().id(id).empresa(tienda).bpmnId(bpmnId).nombre(nombre)
                .tipoActividad(tipo).lane(lane).posicionX(x).posicionY(y).build();
        actividades.add(actividad);
        return actividad;
    }

    private Gateway gateway(Long id, String bpmnId, String nombre, TipoGateway tipo, Lane lane, int x, int y) {
        Gateway gateway = Gateway.builder().id(id).empresa(tienda).bpmnId(bpmnId).nombre(nombre).tipoGateway(tipo)
                .lane(lane).posicionX(x).posicionY(y).build();
        gateways.add(gateway);
        return gateway;
    }

    private Evento evento(Long id, String bpmnId, String nombre, TipoEvento tipo, Lane lane, int x, int y) {
        Evento evento = Evento.builder().id(id).empresa(tienda).bpmnId(bpmnId).nombre(nombre).tipoEvento(tipo)
                .lane(lane).posicionX(x).posicionY(y).build();
        eventos.add(evento);
        return evento;
    }

    private Arco arco(Long id, String bpmnId, NodoFlujo origen, NodoFlujo destino, String etiqueta, String condicion,
            boolean porDefecto, int orden) {
        Arco arco = Arco.builder().id(id).empresa(tienda).bpmnId(bpmnId).origen(origen).destino(destino)
                .etiqueta(etiqueta).condicion(condicion).porDefecto(porDefecto).orden(orden).pool(poolTienda).build();
        arcos.add(arco);
        return arco;
    }

    private Mensaje mensaje(Long id, String bpmnId, String nombre, Pool origen, Pool destino) {
        Mensaje mensaje = Mensaje.builder().id(id).empresa(tienda).proceso(proceso).bpmnId(bpmnId).nombre(nombre)
                .poolOrigen(origen).poolDestino(destino).build();
        mensajes.add(mensaje);
        return mensaje;
    }

    private Correlacion correlacion(Long id, Mensaje mensaje, String criterio, String campo, PoliticaSinCaso sinCaso) {
        Correlacion correlacion = Correlacion.builder().id(id).empresa(tienda).mensaje(mensaje).criterio(criterio)
                .campo(campo).sinCaso(sinCaso).build();
        correlaciones.put(mensaje.getId(), correlacion);
        return correlacion;
    }

    private Artefacto artefacto(Long id, String bpmnId, TipoArtefacto tipo, String texto, Pool pool, Lane lane, int x,
            int y, int ancho, int alto) {
        Artefacto artefacto = Artefacto.builder().id(id).empresa(tienda).proceso(proceso).bpmnId(bpmnId).tipo(tipo)
                .texto(texto).pool(pool).lane(lane).posicionX(x).posicionY(y).ancho(ancho).alto(alto).build();
        artefactos.add(artefacto);
        return artefacto;
    }

    /** Un nodo del plan que cada prueba puede cambiar antes de aplicarlo. */
    private static final class NodoPlan {

        private final String bpmnId;
        private PlanBpmn.Clase clase;
        private String lane;
        private TipoActividad tipoActividad;
        private TipoGateway tipoGateway;
        private TipoEvento tipoEvento;
        private String nombre;
        private String descripcion;
        private int x;
        private int y;
        private String adjuntoA;
        private Boolean interrumpe;
        private TipoTemporizador tipoTemporizador;
        private String temporizador;
        private String padre;
        private Long procesoLlamado;
        private Formulario formulario;
        private AsignacionResponse asignacion;

        private NodoPlan(String bpmnId, PlanBpmn.Clase clase, String lane, String nombre, int x, int y) {
            this.bpmnId = bpmnId;
            this.clase = clase;
            this.lane = lane;
            this.nombre = nombre;
            this.x = x;
            this.y = y;
        }

        static NodoPlan actividad(String bpmnId, String lane, TipoActividad tipo, String nombre, int x, int y) {
            NodoPlan nodo = new NodoPlan(bpmnId, PlanBpmn.Clase.ACTIVIDAD, lane, nombre, x, y);
            nodo.tipoActividad = tipo;
            return nodo;
        }

        static NodoPlan gateway(String bpmnId, String lane, TipoGateway tipo, String nombre, int x, int y) {
            NodoPlan nodo = new NodoPlan(bpmnId, PlanBpmn.Clase.GATEWAY, lane, nombre, x, y);
            nodo.tipoGateway = tipo;
            return nodo;
        }

        static NodoPlan evento(String bpmnId, String lane, TipoEvento tipo, String nombre, int x, int y) {
            NodoPlan nodo = new NodoPlan(bpmnId, PlanBpmn.Clase.EVENTO, lane, nombre, x, y);
            nodo.tipoEvento = tipo;
            return nodo;
        }

        PlanBpmn.Nodo armar() {
            return new PlanBpmn.Nodo(bpmnId, lane, clase, tipoActividad, tipoGateway, tipoEvento, nombre, descripcion,
                    x, y, adjuntoA, interrumpe, tipoTemporizador, temporizador, padre, procesoLlamado, formulario,
                    asignacion);
        }
    }

    /** Un flujo de mensaje del plan que cada prueba puede cambiar antes de aplicarlo. */
    private static final class MensajePlan {

        private final String bpmnId;
        private String nombre;
        private String contenido;
        private String poolOrigen;
        private String poolDestino;
        private String nodoOrigen;
        private String nodoDestino;
        private TipoDestino tipoDestino;
        private AccionSiFalla siFalla = AccionSiFalla.CONTINUAR;
        private String manejoError;
        private boolean origenExterno;
        private List<CampoDeMensaje> campos = List.of();
        private String usoDeLosDatos;
        private String variable;
        private String respuesta;
        private PlanBpmn.Correlacion correlacion;

        MensajePlan(String bpmnId, String nombre, String poolOrigen, String poolDestino) {
            this.bpmnId = bpmnId;
            this.nombre = nombre;
            this.poolOrigen = poolOrigen;
            this.poolDestino = poolDestino;
        }

        PlanBpmn.Mensaje armar() {
            return new PlanBpmn.Mensaje(bpmnId, nombre, contenido, poolOrigen, poolDestino, nodoOrigen, nodoDestino,
                    tipoDestino, siFalla, manejoError, origenExterno, campos, usoDeLosDatos, variable, respuesta,
                    correlacion);
        }
    }
}
