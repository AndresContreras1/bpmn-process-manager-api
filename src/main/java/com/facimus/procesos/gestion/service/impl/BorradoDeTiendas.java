package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.trabajos.ColaDeTrabajos;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 35: el borrado de una tienda que cumplio la gracia de su baja. Borra cada fila suya, tabla por tabla, de las que
 * apuntan a otras hacia las que nadie apunta, y de la fila de la tienda deja una lapida: el id y las fechas de la baja
 * y del borrado, sin nada que diga cual era. Los procesos que otras tiendas le compartian se van con ella.
 * <p>
 * Las sentencias estan escritas una por una, y no armadas desde el catalogo de la base, para que se lean y se revisen:
 * BorradoDeTiendasTest comprueba que cubren toda tabla con {@code empresa_id} y que su orden respeta las claves
 * foraneas, asi que una tabla nueva sin su linea aqui rompe la prueba y no el borrado.
 */
@Component
public class BorradoDeTiendas {

    /** De las hojas a la raiz: cuando le llega el turno a una tabla, ya no queda nada de la tienda que apunte a ella. */
    static final List<String> BORRADOS = List.of(
            "delete from procesos_compartidos where empresa_invitada_id = ?",
            "delete from refresh_tokens where empresa_id = ?",
            "delete from sesiones where empresa_id = ?",
            "delete from enlaces_de_un_uso where empresa_id = ?",
            "delete from codigos_de_recuperacion where empresa_id = ?",
            "delete from vinculos_pendientes where empresa_id = ?",
            "delete from identidades_externas where empresa_id = ?",
            "delete from dominios_verificados where empresa_id = ?",
            "delete from segundos_factores where empresa_id = ?",
            "delete from passkeys where empresa_id = ?",
            "delete from claves_idempotencia where empresa_id = ?",
            "delete from exportaciones where empresa_id = ?",
            "delete from trabajos where empresa_id = ?",
            "delete from notificaciones where empresa_id = ?",
            "delete from preferencias_notificacion where empresa_id = ?",
            "delete from comentarios where empresa_id = ?",
            "delete from membresias_rol where empresa_id = ?",
            "delete from eventos_caso where empresa_id = ?",
            "delete from actividades_caso where empresa_id = ?",
            "delete from mensajes_salientes where empresa_id = ?",
            "delete from mensajes_entrantes where empresa_id = ?",
            "delete from casos where empresa_id = ?",
            "delete from presencias where empresa_id = ?",
            "delete from bloqueos_edicion where empresa_id = ?",
            "delete from revisiones_borrador where empresa_id = ?",
            "delete from extension_xml where empresa_id = ?",
            "delete from importaciones where empresa_id = ?",
            "delete from formas_bpmn where empresa_id = ?",
            "delete from aristas_bpmn where empresa_id = ?",
            "delete from manuales where empresa_id = ?",
            "delete from caracterizaciones where empresa_id = ?",
            "delete from documentacion where empresa_id = ?",
            "delete from versiones_documento where empresa_id = ?",
            "delete from documentos where empresa_id = ?",
            "delete from asociaciones where empresa_id = ?",
            "delete from artefactos where empresa_id = ?",
            "delete from correlaciones where empresa_id = ?",
            "delete from mensajes where empresa_id = ?",
            "delete from arcos where empresa_id = ?",
            "delete from nodos_flujo where empresa_id = ?",
            "delete from lanes where empresa_id = ?",
            "delete from pools where empresa_id = ?",
            "delete from revisiones_ia where empresa_id = ?",
            "delete from historial_cambios where empresa_id = ?",
            "delete from versiones_proceso where empresa_id = ?",
            "delete from procesos_compartidos where empresa_id = ?",
            "delete from concesiones where empresa_id = ?",
            "delete from procesos where empresa_id = ?",
            "delete from carpetas where empresa_id = ?",
            "delete from roles_proceso where empresa_id = ?",
            "delete from configuracion_tienda where empresa_id = ?",
            "delete from usuarios where empresa_id = ?");

    /** Lo que queda de la tienda: ni su nombre, ni su NIT, que otra puede volver a registrar, ni su correo. */
    static final String LAPIDA = "update empresas set nombre = ?, nit = ?, correo_contacto = ?, borrada_en = ? "
            + "where id = ?";

    /**
     * PR 61: el trabajo de la cola que borra los archivos de una tienda del almacen. PR 66: y que despues emite su
     * certificado de borrado.
     */
    public static final String BORRAR_ARCHIVOS = "almacen.borrar-tienda";

    /** PR 66: la ultima exportacion que quedo lista, la que el certificado nombra por su huella. */
    static final String ULTIMA_EXPORTACION = """
            select id, sha256, lista_en from exportaciones
            where empresa_id = ? and sha256 is not null order by id desc limit 1
            """;

    private final JdbcTemplate jdbc;
    private final ColaDeTrabajos cola;
    private final JsonMapper json;

    public BorradoDeTiendas(JdbcTemplate jdbc, ColaDeTrabajos cola, JsonMapper json) {
        this.jdbc = jdbc;
        this.cola = cola;
        this.json = json;
    }

    /** Todo o nada: si una sentencia falla, la tienda queda como estaba y la purga de la noche siguiente reintenta. */
    @Transactional
    public int borrar(Long empresaId, LocalDateTime ahora) {
        // PR 66: lo que el certificado dice de antes del borrado: cuando se pidio la baja y la ultima exportacion
        LocalDateTime bajaSolicitadaEn = jdbc.queryForObject("select baja_solicitada_en from empresas where id = ?",
                LocalDateTime.class, empresaId);
        UltimaExportacion ultima = jdbc.query(ULTIMA_EXPORTACION, (fila, numero) -> new UltimaExportacion(
                fila.getLong("id"), fila.getString("sha256"), fila.getObject("lista_en", LocalDateTime.class)),
                empresaId).stream().findFirst().orElse(null);
        Map<String, Integer> porTabla = new LinkedHashMap<>();
        int filas = 0;
        for (String borrado : BORRADOS) {
            int borradas = jdbc.update(borrado, empresaId);
            porTabla.merge(tabla(borrado), borradas, Integer::sum);
            filas += borradas;
        }
        String marca = "borrada-" + empresaId;
        jdbc.update(LAPIDA, "Tienda borrada", marca, marca + "@borrada.invalid", ahora, empresaId);
        // PR 61: los archivos de sus documentos. El almacen no es transaccional, asi que va a la cola en esta misma
        // transaccion, como trabajo del sistema (los de la tienda se acaban de borrar): se reintenta hasta que sale.
        // PR 66: el trabajo lleva lo que el certificado de borrado cuenta, que despues de este commit ya no existe.
        cola.encolar(null, BORRAR_ARCHIVOS, json.writeValueAsString(new TiendaBorrada(empresaId, bajaSolicitadaEn,
                ahora, porTabla, filas, ultima)));
        return filas;
    }

    /** La tabla de una sentencia de {@link #BORRADOS}: lo que va entre {@code delete from} y {@code where}. */
    static String tabla(String sentencia) {
        String resto = sentencia.substring("delete from ".length());
        return resto.substring(0, resto.indexOf(' '));
    }

    /**
     * PR 66: lo que se sabe de una tienda recien borrada, sin nada que diga cual era ni quien la usaba: su id, las
     * fechas, cuantas filas se borraron de cada tabla y la huella de su ultima exportacion, si la hubo.
     */
    public record TiendaBorrada(Long empresaId, LocalDateTime bajaSolicitadaEn, LocalDateTime borradaEn,
            Map<String, Integer> filas, int totalDeFilas, UltimaExportacion ultimaExportacion) {
    }

    /** PR 66: la ultima exportacion lista de la tienda: cual, su huella y cuando quedo lista. */
    public record UltimaExportacion(Long id, String sha256, LocalDateTime listaEn) {
    }
}
