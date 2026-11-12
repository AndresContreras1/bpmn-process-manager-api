package com.facimus.procesos.modelado.repository;

import org.springframework.data.jpa.domain.Specification;

import com.facimus.procesos.modelado.model.Comentario;
import com.facimus.procesos.modelado.model.EstadoHilo;

import jakarta.persistence.criteria.Subquery;

/** PR 63: los filtros de los hilos de comentarios de un proceso. */
public final class ComentarioSpecifications {

    private static final String ELIMINADO_EN = "eliminadoEn";
    private static final String RESUELTO_EN = "resueltoEn";

    private ComentarioSpecifications() {
    }

    /**
     * Los hilos de un proceso, con los filtros que lleguen: un filtro vacio no filtra. Un hilo cuyo primer comentario
     * se borro solo aparece mientras le quede alguna respuesta.
     */
    public static Specification<Comentario> hilos(Long empresaId, Long procesoId, String bpmnId, Integer version,
            EstadoHilo estado) {
        return (raiz, consulta, cb) -> {
            Subquery<Long> respuestas = consulta.subquery(Long.class);
            var respuesta = respuestas.from(Comentario.class);
            respuestas.select(respuesta.get("id")).where(cb.equal(respuesta.get("padreId"), raiz.get("id")),
                    cb.isNull(respuesta.get(ELIMINADO_EN)));
            var predicado = cb.and(cb.equal(raiz.get("empresa").get("id"), empresaId),
                    cb.equal(raiz.get("procesoId"), procesoId), cb.isNull(raiz.get("padreId")),
                    cb.or(cb.isNull(raiz.get(ELIMINADO_EN)), cb.exists(respuestas)));
            if (bpmnId != null) {
                predicado = cb.and(predicado, cb.equal(raiz.get("bpmnId"), bpmnId));
            }
            if (version != null) {
                predicado = cb.and(predicado, cb.equal(raiz.get("versionNumero"), version));
            }
            if (estado == EstadoHilo.ABIERTO) {
                predicado = cb.and(predicado, cb.isNull(raiz.get(RESUELTO_EN)));
            } else if (estado == EstadoHilo.RESUELTO) {
                predicado = cb.and(predicado, cb.isNotNull(raiz.get(RESUELTO_EN)));
            }
            return predicado;
        };
    }
}
