package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.event.IdiomaDeTiendaCambiado;

/**
 * PR 65: cuando una tienda cambia su idioma, sus vectores de busqueda se rehacen con las raices del nuevo. No en la
 * peticion que lo cambia: una tienda con mucho texto en sus documentos pasaria del tope por sentencia, y el cambio de
 * idioma fallaria siempre. Se encola un trabajo en la misma transaccion, y el trabajo recorre cada tabla por tramos de
 * ids, cada tramo en su propia transaccion corta ({@code rehacer_busqueda} de V56).
 * <p>Mientras corre, la busqueda ya usa el idioma nuevo, y lo que todavia no se rehizo solo se encuentra por las
 * palabras que se escriben igual en los dos. Es idempotente: si falla a medias, el reintento empieza de nuevo.
 */
@Component
public class ReconstruccionDeBusqueda implements ManejadorDeTrabajo {

    /** El tipo de trabajo de la cola que rehace los vectores de una tienda. */
    public static final String TRABAJO_DE_RECONSTRUCCION = "busqueda.rehacer";

    static final String TRAMO = "select rehacer_busqueda(?, ?, ?, ?)";

    /** Cada tabla con vectores, y cuantas filas suyas van por tramo: una version de un documento trae mucho texto. */
    record Tabla(String nombre, int porTramo) {
    }

    static final List<Tabla> TABLAS = List.of(new Tabla("procesos", 500), new Tabla("elementos_buscables", 500),
            new Tabla("comentarios", 500), new Tabla("versiones_documento", 10));

    /** La cola conoce a cada manejador, este incluido: se pide al encolar, no al crearse. */
    private final ObjectProvider<ColaDeTrabajos> cola;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public ReconstruccionDeBusqueda(ObjectProvider<ColaDeTrabajos> cola, JdbcTemplate jdbc, PlatformTransactionManager transacciones) {
        this.cola = cola;
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Corre dentro de la transaccion que cambia el idioma: si no se confirma, no queda trabajo. */
    @EventListener
    public void alCambiarElIdioma(IdiomaDeTiendaCambiado cambio) {
        cola.getObject().encolar(cambio.empresaId(), TRABAJO_DE_RECONSTRUCCION, String.valueOf(cambio.empresaId()));
    }

    @Override
    public String tipo() {
        return TRABAJO_DE_RECONSTRUCCION;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        Long empresaId = trabajo.empresaId();
        for (Tabla tabla : TABLAS) {
            Long desde = 0L;
            while (desde != null) {
                desde = tramo(empresaId, tabla, desde);
            }
        }
    }

    /** Un tramo, en su transaccion; dice el ultimo id que rehizo, o nada si la tabla ya no tenia mas. */
    private Long tramo(Long empresaId, Tabla tabla, Long desde) {
        return transaccion.execute(estado -> jdbc.queryForObject(TRAMO, Long.class, empresaId, tabla.nombre(), desde,
                tabla.porTramo()));
    }
}
