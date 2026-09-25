package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.ALMACEN;
import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.condiciones.Condicion;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * El grafo con el que corre un caso: lo que la instantanea de la version dice del pool de la tienda, indexado y con
 * las condiciones ya compiladas. Se arma sin base de datos, asi que se prueba armandolo.
 */
class GrafoDeVersionTest {

    private static final Long BODEGA = 7L;

    @Test
    @DisplayName("Solo entran los nodos del pool de la tienda: lo que otro participante dibuje por dentro no corre")
    void grafo_soloElPoolDeLaTienda() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.dejaDeSerCajaNegra("Carrier");
        armado.lane("Carrier", "Dispatch");
        Long ajena = armado.actividad("Dispatch", "Plan the route", TipoActividad.USUARIO);

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.poolDeLaTienda()).contains(armado.id(TIENDA));
        assertThat(grafo.nodo(armado.id("Pick and pack items"))).isPresent();
        assertThat(grafo.nodo(ajena)).isEmpty();
    }

    @Test
    @DisplayName("Cada nodo sabe que es, en que lane esta y a que rol pertenece esa lane")
    void nodo_conoceSuTipoYSuRol() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        Long lane = armado.lane(TIENDA, ALMACEN, BODEGA);
        Long empacar = armado.actividad(ALMACEN, "Pick and pack items", TipoActividad.USUARIO);

        NodoDeLaVersion nodo = GrafoDeVersion.de(armado.diagrama()).nodo(empacar).orElseThrow();

        assertThat(nodo.nombre()).isEqualTo("Pick and pack items");
        assertThat(nodo.tipo()).isEqualTo(TipoNodoCaso.ACTIVIDAD);
        assertThat(nodo.subtipo()).isEqualTo("USUARIO");
        assertThat(nodo.laneId()).isEqualTo(lane);
        assertThat(nodo.rolProcesoId()).isEqualTo(BODEGA);
        assertThat(nodo.esTareaDeUsuario()).isTrue();
    }

    @Test
    @DisplayName("El proceso de la demo empieza por mensaje, asi que no tiene inicio a mano")
    void demo_empiezaPorMensaje() {
        GrafoDeVersion grafo = GrafoDeVersion.de(DiagramaArmado.demo().diagrama());

        assertThat(grafo.inicioAMano()).isEmpty();
        assertThat(grafo.inicioPorMensaje()).isPresent()
                .get().extracting(NodoDeLaVersion::nombre).isEqualTo("Order received");
    }

    @Test
    @DisplayName("Un proceso con evento de inicio se abre a mano por ese evento")
    void inicioAMano_encuentraElEvento() {
        DiagramaArmado armado = unProcesoMinimo();

        assertThat(GrafoDeVersion.de(armado.diagrama()).inicioAMano())
                .get().extracting(NodoDeLaVersion::nombre).isEqualTo("Start");
    }

    @Test
    @DisplayName("Las salidas de un gateway salen en el orden en que las evalua, y el id desempata")
    void salidas_enOrdenDeEvaluacion() {
        DiagramaArmado armado = unProcesoMinimo();
        Long gateway = armado.gateway(VENTAS, "Decide", TipoGateway.EXCLUSIVO);
        armado.actividad(VENTAS, "Segunda", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Primera", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Tercera", TipoActividad.USUARIO);
        armado.arcoCon("Decide", "Segunda", "order.total > 100", 5);
        armado.arcoCon("Decide", "Primera", "order.vip == true", 1);
        armado.arcoCon("Decide", "Tercera", "order.total > 10", 5);

        assertThat(GrafoDeVersion.de(armado.diagrama()).salidasDe(gateway))
                .extracting(arco -> arco.condicion().texto())
                .containsExactly("order.vip == true", "order.total > 100", "order.total > 10");
    }

    @Test
    @DisplayName("La condicion de cada salida llega compilada; la salida por defecto no lleva ninguna")
    void condiciones_lleganCompiladas() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long gateway = armado.id("Payment approved?");

        var salidas = GrafoDeVersion.de(armado.diagrama()).salidasDe(gateway);

        assertThat(salidas).hasSize(2);
        assertThat(salidas.getFirst().condicion()).isNotNull()
                .extracting(Condicion::texto).isEqualTo("payment.status == APPROVED");
        assertThat(salidas.getLast().porDefecto()).isTrue();
        assertThat(salidas.getLast().condicion()).isNull();
    }

    @Test
    @DisplayName("Un arco cuyo origen o destino esta fuera del pool de la tienda no entra al grafo")
    void arcos_fueraDelPool_noEntran() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.pool("Carrier", TipoParticipante.PROVEEDOR, false, Integracion.TRANSPORTE);
        armado.lane("Carrier", "Dispatch");
        armado.actividad("Dispatch", "Plan the route", TipoActividad.USUARIO);
        armado.arco("Start", "Plan the route");

        assertThat(GrafoDeVersion.de(armado.diagrama()).salidasDe(armado.id("Start"))).isEmpty();
    }

    @Test
    @DisplayName("La alcanzabilidad dice a donde puede llegar un token desde cada nodo")
    void alcanzabilidad_diceQueQuedaPorDelante() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long gateway = armado.id("Payment approved?");

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.alcanza(gateway, armado.id("Order shipped"))).isTrue();
        assertThat(grafo.alcanza(gateway, armado.id("Order cancelled"))).isTrue();
        assertThat(grafo.alcanza(armado.id("Cancel order"), armado.id("Pick and pack items"))).isFalse();
        assertThat(grafo.alcanza(armado.id("Order shipped"), gateway)).isFalse();
    }

    @Test
    @DisplayName("Un ciclo en el diagrama no deja la alcanzabilidad dando vueltas")
    void alcanzabilidad_conCiclo_termina() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.actividad(VENTAS, "Revisar", TipoActividad.USUARIO);
        armado.arco("Start", "Revisar");
        armado.arco("Revisar", "Start");

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.alcanza(armado.id("Start"), armado.id("Start"))).isTrue();
        assertThat(grafo.alcanza(armado.id("Revisar"), armado.id("Revisar"))).isTrue();
    }

    @Test
    @DisplayName("Las entradas de un nodo son cuantos caminos llegan a el, que es lo que espera un join")
    void entradas_cuentanLosCaminosQueLleganAlJoin() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.gateway(VENTAS, "Fork", TipoGateway.PARALELO);
        armado.gateway(VENTAS, "Join", TipoGateway.PARALELO);
        armado.actividad(VENTAS, "Izquierda", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Derecha", TipoActividad.USUARIO);
        armado.arco("Start", "Fork");
        armado.arco("Fork", "Izquierda");
        armado.arco("Fork", "Derecha");
        armado.arco("Izquierda", "Join");
        armado.arco("Derecha", "Join");

        assertThat(GrafoDeVersion.de(armado.diagrama()).entradasDe(armado.id("Join"))).hasSize(2);
    }

    @Test
    @DisplayName("Un diagrama sin pool de la tienda da un grafo vacio, no una excepcion")
    void sinPoolDeLaTienda_grafoVacio() {
        DiagramaArmado armado = new DiagramaArmado();
        armado.pool("Carrier", TipoParticipante.PROVEEDOR, true, Integracion.TRANSPORTE);

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.poolDeLaTienda()).isEmpty();
        assertThat(grafo.estaVacio()).isTrue();
        assertThat(grafo.inicioAMano()).isEmpty();
    }

    @Test
    @DisplayName("Una condicion que no compila deja el arco sin condicion en vez de tumbar el caso")
    void condicionRota_noTumbaElGrafo() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.gateway(VENTAS, "Decide", TipoGateway.EXCLUSIVO);
        armado.actividad(VENTAS, "Seguir", TipoActividad.USUARIO);
        armado.arcoCon("Decide", "Seguir", "esto no es una condicion");

        var salidas = GrafoDeVersion.de(armado.diagrama()).salidasDe(armado.id("Decide"));

        assertThat(salidas).hasSize(1);
        assertThat(salidas.getFirst().condicion()).isNull();
    }

    @Test
    @DisplayName("Un nodo sabe si empieza el proceso, si lo termina y si elige por condicion")
    void nodo_sabeQuePapelTieneEnElFlujo() {
        DiagramaArmado armado = DiagramaArmado.demo();

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        NodoDeLaVersion inicio = grafo.nodo(armado.id("Order received")).orElseThrow();
        assertThat(inicio.esEvento()).isTrue();
        assertThat(inicio.empiezaElProceso()).isTrue();
        assertThat(inicio.terminaElProceso()).isFalse();

        NodoDeLaVersion fin = grafo.nodo(armado.id("Order shipped")).orElseThrow();
        assertThat(fin.terminaElProceso()).isTrue();
        assertThat(fin.empiezaElProceso()).isFalse();

        NodoDeLaVersion gateway = grafo.nodo(armado.id("Payment approved?")).orElseThrow();
        assertThat(gateway.esGateway()).isTrue();
        assertThat(gateway.eligePorCondicion()).isTrue();
        assertThat(gateway.esGatewayDe(TipoGateway.EXCLUSIVO)).isTrue();
        assertThat(gateway.esGatewayDe(TipoGateway.PARALELO)).isFalse();

        NodoDeLaVersion envio = grafo.nodo(armado.id("Request payment authorization")).orElseThrow();
        assertThat(envio.esActividad()).isTrue();
        assertThat(envio.esTareaDeUsuario()).isFalse();
        assertThat(envio.eligePorCondicion()).isFalse();
    }

    @Test
    @DisplayName("Un paralelo no elige: sigue todas sus salidas")
    void gatewayParalelo_noEligePorCondicion() {
        DiagramaArmado armado = unProcesoMinimo();
        Long fork = armado.gateway(VENTAS, "Fork", TipoGateway.PARALELO);

        assertThat(GrafoDeVersion.de(armado.diagrama()).nodo(fork).orElseThrow().eligePorCondicion()).isFalse();
    }

    @Test
    @DisplayName("Una salida se nombra con su etiqueta y su condicion, que es lo que explica por donde se fue")
    void salida_seNombraConLoQueLaExplica() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.gateway(VENTAS, "Decide", TipoGateway.EXCLUSIVO);
        armado.actividad(VENTAS, "Aprobado", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Rechazado", TipoActividad.USUARIO);
        armado.arcoConEtiqueta("Decide", "Aprobado", "Approved", "payment.status == APPROVED");
        armado.arcoCon("Decide", "Rechazado", "payment.status == DECLINED");

        var salidas = GrafoDeVersion.de(armado.diagrama()).salidasDe(armado.id("Decide"));

        assertThat(salidas.getFirst().comoSeLlama()).isEqualTo("Approved (payment.status == APPROVED)");
        assertThat(salidas.getLast().comoSeLlama()).isEqualTo("payment.status == DECLINED");
    }

    @Test
    @DisplayName("La salida por defecto se nombra como tal, con o sin etiqueta")
    void salidaPorDefecto_seNombraComoTal() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.gateway(VENTAS, "Decide", TipoGateway.EXCLUSIVO);
        armado.actividad(VENTAS, "Rechazado", TipoActividad.USUARIO);
        armado.arcoPorDefecto("Decide", "Rechazado");

        assertThat(GrafoDeVersion.de(armado.diagrama()).salidasDe(armado.id("Decide")).getFirst().comoSeLlama())
                .isEqualTo("la salida por defecto");
    }

    @Test
    @DisplayName("Una salida sin etiqueta ni condicion se nombra sin inventar nada")
    void salidaSinNada_seNombraSinInventar() {
        DiagramaArmado armado = unProcesoMinimo();
        armado.actividad(VENTAS, "Seguir", TipoActividad.USUARIO);
        armado.arco("Start", "Seguir");

        assertThat(GrafoDeVersion.de(armado.diagrama()).salidasDe(armado.id("Start")).getFirst().comoSeLlama())
                .isEqualTo("la salida sin condicion");
    }

    @Test
    @DisplayName("El nodo que espera un mensaje dice cual, que es lo que se responde a quien intenta abrir el caso")
    void mensajeQueEspera_diceSuNombre() {
        DiagramaArmado armado = DiagramaArmado.demo();

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.mensajeQueEspera(armado.id("Order received"))).contains("Order placed");
        assertThat(grafo.mensajeQueEspera(armado.id("Receive order"))).isEmpty();
    }

    /** Lo minimo que se puede publicar: el pool de la tienda, una lane y un evento de inicio. */
    private static DiagramaArmado unProcesoMinimo() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 1L);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        return armado;
    }
}
