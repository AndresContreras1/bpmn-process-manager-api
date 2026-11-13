package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.event.IdiomaDeTiendaCambiado;

/** PR 65: los vectores de una tienda se rehacen en un trabajo de la cola, por tramos cortos, al cambiar su idioma. */
@ExtendWith(MockitoExtension.class)
class ReconstruccionDeBusquedaTest {

    private static final Long EMPRESA = 7L;

    @Mock
    private ColaDeTrabajos cola;
    @Mock
    private ObjectProvider<ColaDeTrabajos> proveedorDeLaCola;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PlatformTransactionManager transacciones;

    private ReconstruccionDeBusqueda reconstruccion;

    @BeforeEach
    void preparar() {
        reconstruccion = new ReconstruccionDeBusqueda(proveedorDeLaCola, jdbc, transacciones);
    }

    @Test
    @DisplayName("un cambio de idioma encola el trabajo de su tienda, sin rehacer nada en la peticion")
    void encola() {
        when(proveedorDeLaCola.getObject()).thenReturn(cola);

        reconstruccion.alCambiarElIdioma(new IdiomaDeTiendaCambiado(EMPRESA, "en-US"));

        verify(cola).encolar(EMPRESA, "busqueda.rehacer", "7");
        verifyNoInteractions(jdbc, transacciones);
        assertThat(reconstruccion.tipo()).isEqualTo("busqueda.rehacer");
    }

    @Test
    @DisplayName("cada tabla se recorre por tramos, desde el ultimo id del anterior hasta que no queda nada, uno por transaccion")
    void porTramos() {
        when(transacciones.getTransaction(any())).thenAnswer(llamada -> new SimpleTransactionStatus());
        Map<String, List<Long>> ultimos = new HashMap<>();
        ultimos.put("procesos", new ArrayList<>(Arrays.asList(5L, 9L, null)));
        ultimos.put("elementos_buscables", new ArrayList<>(Arrays.asList((Long) null)));
        ultimos.put("comentarios", new ArrayList<>(Arrays.asList(3L, null)));
        ultimos.put("versiones_documento", new ArrayList<>(Arrays.asList(40L, null)));
        List<List<Object>> tramos = new ArrayList<>();
        when(jdbc.queryForObject(eq(ReconstruccionDeBusqueda.TRAMO), eq(Long.class), any(), any(), any(), any()))
                .thenAnswer(llamada -> {
                    List<Object> argumentos = List.of(llamada.getArgument(2), llamada.getArgument(3),
                            llamada.getArgument(4), llamada.getArgument(5));
                    tramos.add(argumentos);
                    return ultimos.get((String) llamada.getArgument(3)).removeFirst();
                });

        reconstruccion.ejecutar(new Trabajo(1, EMPRESA, "busqueda.rehacer", "7", 1, 5));

        assertThat(tramos).containsExactly(
                List.of(EMPRESA, "procesos", 0L, 500), List.of(EMPRESA, "procesos", 5L, 500),
                List.of(EMPRESA, "procesos", 9L, 500), List.of(EMPRESA, "elementos_buscables", 0L, 500),
                List.of(EMPRESA, "comentarios", 0L, 500), List.of(EMPRESA, "comentarios", 3L, 500),
                List.of(EMPRESA, "versiones_documento", 0L, 10), List.of(EMPRESA, "versiones_documento", 40L, 10));
        verify(transacciones, times(8)).commit(any());
        verifyNoInteractions(proveedorDeLaCola);
    }
}
