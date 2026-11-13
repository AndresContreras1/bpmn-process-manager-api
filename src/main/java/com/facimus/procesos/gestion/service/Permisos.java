package com.facimus.procesos.gestion.service;

import java.util.Collection;
import java.util.Set;

import com.facimus.procesos.common.model.Accion;

/**
 * PR 60: el unico evaluador de permisos. Tres capas: el rol de acceso pone el techo, las concesiones sobre la carpeta y
 * el proceso deciden, y la separacion de funciones va al final.
 *
 * <p>Una accion sin ninguna concesion en el proceso ni en sus carpetas sigue el comportamiento por defecto: ver,
 * comentar y exportar para todos, modelar, revisar, aprobar y publicar para quien edita, y administrar para el
 * administrador. Con al menos una, queda solo para quienes la tengan. Un recurso que no es de la tienda no se evalua:
 * el servicio responde por el, y un proceso compartido con otra tienda tiene su propia puerta.
 */
public interface Permisos {

    boolean puede(Actor actor, Accion accion, Recurso recurso);

    /** Ver negado responde 404, para no decir que existe; lo demas responde 403. */
    void exigir(Actor actor, Accion accion, Recurso recurso);

    /** Lo mismo cuando solo se tiene el id de quien pide. */
    void exigir(Long empresaId, Long usuarioId, Accion accion, Recurso recurso);

    Set<Accion> acciones(Actor actor, Recurso recurso);

    /** Si el actor tiene una concesion expresa de esa accion, sin contar lo que trae por defecto. */
    boolean concedido(Long empresaId, Long usuarioId, Accion accion, Recurso recurso);

    /** Si el recurso o una de sus carpetas tiene alguna concesion. */
    boolean tieneConcesiones(Long empresaId, Recurso recurso);

    Visibilidad visibilidad(Actor actor);

    /**
     * PR 64: de esas personas de la tienda, las que pueden hacer cada una de esas acciones sobre el recurso. Decide lo
     * mismo que preguntar por cada una, pero lee el recurso, las concesiones, las carpetas y los roles una sola vez.
     */
    Set<Long> quienesPueden(Long empresaId, Recurso recurso, Collection<Actor> actores, Set<Accion> acciones);
}
