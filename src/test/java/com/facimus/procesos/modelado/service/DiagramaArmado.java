package com.facimus.procesos.modelado.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * Arma diagramas para el diagnostico con la misma forma que devuelve la API: listas planas que se enlazan por id.
 * Los elementos se nombran, no se numeran, asi que una prueba dice "el gateway Payment approved?" y no un id.
 *
 * <p>{@link #demo()} arma el proceso de la tienda de ejemplo, que cumple todas las reglas. Cada prueba parte de el
 * y rompe una sola cosa, que es justo el hallazgo que espera.
 */
class DiagramaArmado {

    static final String TIENDA = "Demo Store";
    static final String VENTAS = "Sales";
    static final String ALMACEN = "Warehouse";

    private final List<PoolResponse> pools = new ArrayList<>();
    private final List<LaneResponse> lanes = new ArrayList<>();
    private final List<ActividadResponse> actividades = new ArrayList<>();
    private final List<GatewayResponse> gateways = new ArrayList<>();
    private final List<EventoResponse> eventos = new ArrayList<>();
    private final List<ArcoResponse> arcos = new ArrayList<>();
    private final List<MensajeResponse> mensajes = new ArrayList<>();
    private final List<CorrelacionResponse> correlaciones = new ArrayList<>();
    private final List<String> nombres = new ArrayList<>();

    /**
     * El proceso de la tienda de ejemplo: un pedido entra por mensaje, se cobra, y segun la respuesta de la pasarela
     * se empaca y se envia o se cancela. Cumple todas las reglas del catalogo.
     */
    static DiagramaArmado demo() {
        DiagramaArmado armado = new DiagramaArmado();
        armado.pool(TIENDA, TipoParticipante.EMPRESA, false, Integracion.NINGUNA);
        armado.pool("Customer", TipoParticipante.CLIENTE, true, Integracion.CLIENTE);
        armado.pool("Payment gateway", TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS);
        armado.pool("Carrier", TipoParticipante.PROVEEDOR, true, Integracion.TRANSPORTE);
        armado.lane(TIENDA, VENTAS);
        armado.lane(TIENDA, ALMACEN);

        armado.evento(VENTAS, "Order received", TipoEvento.MENSAJE_INICIO);
        armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Request payment authorization", TipoActividad.ENVIO);
        armado.evento(VENTAS, "Payment result received", TipoEvento.MENSAJE_INTERMEDIO);
        armado.gateway(VENTAS, "Payment approved?", TipoGateway.EXCLUSIVO);
        armado.actividad(VENTAS, "Cancel order", TipoActividad.SERVICIO);
        armado.evento(VENTAS, "Order cancelled", TipoEvento.FIN);
        armado.actividad(ALMACEN, "Pick and pack items", TipoActividad.USUARIO);
        armado.actividad(ALMACEN, "Ship order", TipoActividad.ENVIO);
        armado.evento(ALMACEN, "Shipment confirmed", TipoEvento.MENSAJE_INTERMEDIO);
        armado.evento(ALMACEN, "Order shipped", TipoEvento.FIN);

        armado.arco("Order received", "Receive order");
        armado.arco("Receive order", "Request payment authorization");
        armado.arco("Request payment authorization", "Payment result received");
        armado.arco("Payment result received", "Payment approved?");
        armado.arcoCon("Payment approved?", "Pick and pack items", "payment.status == APPROVED");
        armado.arcoPorDefecto("Payment approved?", "Cancel order");
        armado.arco("Pick and pack items", "Ship order");
        armado.arco("Ship order", "Shipment confirmed");
        armado.arco("Shipment confirmed", "Order shipped");
        armado.arco("Cancel order", "Order cancelled");

        armado.mensaje("Order placed", "Customer", TIENDA, null, "Order received", null,
                AccionSiFalla.CONTINUAR, null, false);
        armado.mensaje("Payment authorization request", TIENDA, "Payment gateway",
                "Request payment authorization", null, TipoDestino.SERVICIO_WEB, AccionSiFalla.MANEJAR_ERROR,
                "Cancel order", false);
        armado.mensaje("Payment authorization result", "Payment gateway", TIENDA, null,
                "Payment result received", null, AccionSiFalla.CONTINUAR, null, false);
        armado.mensaje("Shipment request", TIENDA, "Carrier", "Ship order", null, TipoDestino.COLA,
                AccionSiFalla.CONTINUAR, null, false);
        armado.mensaje("Shipment confirmation", "Carrier", TIENDA, null, "Shipment confirmed", null,
                AccionSiFalla.CONTINUAR, null, true);
        armado.mensaje("Order status notification", TIENDA, "Customer", "Cancel order", null, TipoDestino.CORREO,
                AccionSiFalla.CONTINUAR, null, false);

        armado.respuestaEsperada("Payment authorization request", "Payment authorization result");

        armado.correlacion("Order placed", PoliticaSinCaso.INICIAR_CASO);
        armado.correlacion("Payment authorization request", PoliticaSinCaso.DESCARTAR);
        armado.correlacion("Payment authorization result", PoliticaSinCaso.DESCARTAR);
        armado.correlacion("Shipment request", PoliticaSinCaso.DESCARTAR);
        armado.correlacion("Shipment confirmation", PoliticaSinCaso.DESCARTAR);
        armado.correlacion("Order status notification", PoliticaSinCaso.DESCARTAR);
        return armado;
    }

    /** Un proceso recien creado: su pool y nada mas, que es lo que deja el alta de un proceso. */
    static DiagramaArmado reciennacido() {
        DiagramaArmado armado = new DiagramaArmado();
        armado.pool(TIENDA, TipoParticipante.EMPRESA, false, Integracion.NINGUNA);
        return armado;
    }

    Long pool(String nombre, TipoParticipante tipo, boolean cajaNegra, Integracion integracion) {
        Long id = registrar(nombre);
        pools.add(new PoolResponse(id, nombre, tipo, cajaNegra, integracion, pools.size(), 1L, 0L, null, null, null,
                null));
        return id;
    }

    Long lane(String pool, String nombre) {
        Long id = registrar(nombre);
        lanes.add(new LaneResponse(id, nombre, lanes.size(), id(pool), null, null, 0L, null, null, null, null));
        return id;
    }

    Long actividad(String lane, String nombre, TipoActividad tipo) {
        Long id = registrar(nombre);
        actividades.add(new ActividadResponse(id, nombre, null, tipo, 0, 0, id(lane), 0L, null, null, null, null));
        return id;
    }

    Long gateway(String lane, String nombre, TipoGateway tipo) {
        Long id = registrar(nombre);
        gateways.add(new GatewayResponse(id, nombre, tipo, 0, 0, id(lane), 0L, null, null, null, null));
        return id;
    }

    Long evento(String lane, String nombre, TipoEvento tipo) {
        Long id = registrar(nombre);
        eventos.add(new EventoResponse(id, nombre, tipo, 0, 0, id(lane), 0L, null, null, null, null));
        return id;
    }

    void arco(String origen, String destino) {
        nuevoArco(origen, destino, null, false);
    }

    void arcoCon(String origen, String destino, String condicion) {
        nuevoArco(origen, destino, condicion, false);
    }

    void arcoPorDefecto(String origen, String destino) {
        nuevoArco(origen, destino, null, true);
    }

    /** Quita los flujos que salen de un nodo, para dejarlo sin camino hacia adelante. */
    void quitarSalidasDe(String nodo) {
        arcos.removeIf(arco -> arco.origenId().equals(id(nodo)));
    }

    /** Quita los flujos que entran a un nodo, para dejarlo sin quien lo active. */
    void quitarEntradasDe(String nodo) {
        arcos.removeIf(arco -> arco.destinoId().equals(id(nodo)));
    }

    /** Quita una lane con todo lo que hay dentro, como si nunca se hubiera creado. */
    void quitarLane(String nombre) {
        Long laneId = id(nombre);
        lanes.removeIf(lane -> lane.id().equals(laneId));
        actividades.removeIf(actividad -> actividad.laneId().equals(laneId));
        gateways.removeIf(gateway -> gateway.laneId().equals(laneId));
        eventos.removeIf(evento -> evento.laneId().equals(laneId));
    }

    void mensaje(String nombre, String poolOrigen, String poolDestino, String nodoOrigen, String nodoDestino,
            TipoDestino tipoDestino, AccionSiFalla siFalla, String nodoManejoError, boolean origenExterno) {
        Long id = registrar(nombre);
        mensajes.add(new MensajeResponse(id, nombre, "Contenido de prueba", id(poolOrigen), id(poolDestino),
                id(nodoOrigen), id(nodoDestino), tipoDestino, siFalla, id(nodoManejoError), origenExterno, List.of(),
                null, null, null, 1L, 0L, null, null, null, null));
    }

    /** Declara con que mensaje contesta el otro participante, como la pasarela contesta la autorizacion. */
    void respuestaEsperada(String mensaje, String respuesta) {
        cambiarMensaje(mensaje, viejo -> new MensajeResponse(viejo.id(), viejo.nombre(), viejo.contenido(),
                viejo.poolOrigenId(), viejo.poolDestinoId(), viejo.nodoOrigenId(), viejo.nodoDestinoId(),
                viejo.tipoDestino(), viejo.siFalla(), viejo.nodoManejoErrorId(), viejo.origenExterno(),
                viejo.campos(), viejo.usoDeLosDatos(), viejo.variable(), id(respuesta), viejo.procesoId(),
                viejo.version(), null, null, null, null));
    }

    /** Desancla un mensaje del nodo que lo espera, dejando solo el participante que lo recibe. */
    void desanclarDestinoDe(String mensaje) {
        cambiarMensaje(mensaje, viejo -> new MensajeResponse(viejo.id(), viejo.nombre(), viejo.contenido(),
                viejo.poolOrigenId(), viejo.poolDestinoId(), viejo.nodoOrigenId(), null, viejo.tipoDestino(),
                viejo.siFalla(), viejo.nodoManejoErrorId(), viejo.origenExterno(), viejo.campos(),
                viejo.usoDeLosDatos(), viejo.variable(), viejo.respuestaEsperadaId(), viejo.procesoId(),
                viejo.version(), null, null, null, null));
    }

    /** Quita un mensaje del diagrama con su correlacion, como si nunca se hubiera creado. */
    void quitarMensaje(String nombre) {
        Long mensajeId = id(nombre);
        mensajes.removeIf(mensaje -> mensaje.id().equals(mensajeId));
        correlaciones.removeIf(correlacion -> correlacion.mensajeId().equals(mensajeId));
    }

    /** Quita la clave con la que un mensaje encuentra su caso. */
    void quitarCorrelacionDe(String mensaje) {
        correlaciones.removeIf(correlacion -> correlacion.mensajeId().equals(id(mensaje)));
    }

    /** Deja un mensaje a un sistema externo sin decir por donde viaja. */
    void sinTipoDestino(String mensaje) {
        cambiarMensaje(mensaje, viejo -> new MensajeResponse(viejo.id(), viejo.nombre(), viejo.contenido(),
                viejo.poolOrigenId(), viejo.poolDestinoId(), viejo.nodoOrigenId(), viejo.nodoDestinoId(), null,
                viejo.siFalla(), viejo.nodoManejoErrorId(), viejo.origenExterno(), viejo.campos(),
                viejo.usoDeLosDatos(), viejo.variable(), viejo.respuestaEsperadaId(), viejo.procesoId(),
                viejo.version(), null, null, null, null));
    }

    /** Copia un mensaje con su clave, para las pruebas de nombres repetidos. */
    void duplicarMensaje(String nombre) {
        MensajeResponse original = mensajes.stream()
                .filter(mensaje -> mensaje.id().equals(id(nombre)))
                .findFirst().orElseThrow();
        Long copia = siguienteId();
        mensajes.add(new MensajeResponse(copia, original.nombre(), original.contenido(), original.poolOrigenId(),
                original.poolDestinoId(), original.nodoOrigenId(), original.nodoDestinoId(), original.tipoDestino(),
                original.siFalla(), original.nodoManejoErrorId(), original.origenExterno(), original.campos(),
                original.usoDeLosDatos(), original.variable(), null, original.procesoId(), 0L, null, null, null,
                null));
        correlaciones.add(new CorrelacionResponse(siguienteId(), "orderId", "orderId", PoliticaSinCaso.DESCARTAR,
                copia, 0L, null, null, null, null));
    }

    /** Deja la clave de correlacion sin el campo del cuerpo que la lleva. */
    void quitarCampoDeCorrelacionDe(String mensaje) {
        Long mensajeId = id(mensaje);
        correlaciones.replaceAll(correlacion -> correlacion.mensajeId().equals(mensajeId)
                ? new CorrelacionResponse(correlacion.id(), correlacion.criterio(), null, correlacion.sinCaso(),
                        mensajeId, 0L, null, null, null, null)
                : correlacion);
    }

    private void cambiarMensaje(String nombre, UnaryOperator<MensajeResponse> cambio) {
        Long mensajeId = id(nombre);
        mensajes.replaceAll(mensaje -> mensaje.id().equals(mensajeId) ? cambio.apply(mensaje) : mensaje);
    }

    void correlacion(String mensaje, PoliticaSinCaso sinCaso) {
        correlaciones.add(new CorrelacionResponse(siguienteId(), "orderId", "orderId", sinCaso, id(mensaje), 0L, null,
                null, null, null));
    }

    /** El id del flujo que une dos nodos, para las pruebas que preguntan por un arco. */
    Long arcoEntre(String origen, String destino) {
        return arcos.stream()
                .filter(arco -> arco.origenId().equals(id(origen)) && arco.destinoId().equals(id(destino)))
                .map(ArcoResponse::id)
                .findFirst().orElseThrow();
    }

    /** El id con el que el diagrama nombra un elemento; null cuando el nombre no es de ninguno. */
    Long id(String nombre) {
        int posicion = nombre == null ? -1 : nombres.indexOf(nombre);
        return posicion < 0 ? null : (long) (posicion + 1);
    }

    DiagramaResponse diagrama() {
        ProcesoResponse proceso = new ProcesoResponse(1L, "Order fulfillment", "De la compra a la entrega.",
                "Fulfillment", EstadoProceso.BORRADOR, true, null, null, 0L, null, null);
        return new DiagramaResponse(proceso, false, List.copyOf(pools), List.copyOf(lanes), List.copyOf(actividades),
                List.copyOf(gateways), List.copyOf(eventos), List.copyOf(arcos), List.copyOf(mensajes),
                List.copyOf(correlaciones));
    }

    private void nuevoArco(String origen, String destino, String condicion, boolean porDefecto) {
        arcos.add(new ArcoResponse(siguienteId(), null, condicion, porDefecto, 0, id(origen), id(destino), id(TIENDA),
                0L, null, null, null, null));
    }

    /** Cada elemento se queda con el id de su posicion, asi que el diagrama llega ordenado por id, como la API. */
    private Long registrar(String nombre) {
        nombres.add(nombre);
        return (long) nombres.size();
    }

    private Long siguienteId() {
        return registrar("#" + (nombres.size() + 1));
    }
}
