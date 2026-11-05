package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
            "delete from claves_idempotencia where empresa_id = ?",
            "delete from trabajos where empresa_id = ?",
            "delete from membresias_rol where empresa_id = ?",
            "delete from eventos_caso where empresa_id = ?",
            "delete from actividades_caso where empresa_id = ?",
            "delete from mensajes_salientes where empresa_id = ?",
            "delete from mensajes_entrantes where empresa_id = ?",
            "delete from casos where empresa_id = ?",
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
            "delete from procesos where empresa_id = ?",
            "delete from roles_proceso where empresa_id = ?",
            "delete from configuracion_tienda where empresa_id = ?",
            "delete from usuarios where empresa_id = ?");

    /** Lo que queda de la tienda: ni su nombre, ni su NIT, que otra puede volver a registrar, ni su correo. */
    static final String LAPIDA = "update empresas set nombre = ?, nit = ?, correo_contacto = ?, borrada_en = ? "
            + "where id = ?";

    private final JdbcTemplate jdbc;

    public BorradoDeTiendas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Todo o nada: si una sentencia falla, la tienda queda como estaba y la purga de la noche siguiente reintenta. */
    @Transactional
    public int borrar(Long empresaId, LocalDateTime ahora) {
        int filas = 0;
        for (String borrado : BORRADOS) {
            filas += jdbc.update(borrado, empresaId);
        }
        String marca = "borrada-" + empresaId;
        jdbc.update(LAPIDA, "Tienda borrada", marca, marca + "@borrada.invalid", ahora, empresaId);
        return filas;
    }
}
