package com.facimus.procesos.common.auditoria;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.facimus.procesos.common.api.Problemas;
import com.facimus.procesos.common.auditoria.SucesoAuditable.TipoDeActor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * PR 31: lo que un servicio avisa para la bitacora. No la escribe: publica un {@link SucesoAuditable} que queda en
 * el outbox de Spring Modulith en la misma transaccion que lo que paso, y la bitacora lo anota despues del commit.
 * Si lo que paso se deshace, el suceso tampoco queda. Lo que se avisa fuera de una transaccion, como un login
 * fallido, abre una propia: sin ella, el aviso no llegaria a nadie.
 */
@Component
public class Auditoria {

    private static final int LARGO_DEL_NAVEGADOR = 255;

    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;

    public Auditoria(ApplicationEventPublisher eventos, PlatformTransactionManager transacciones) {
        this.eventos = eventos;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Algo que hizo una persona, o la API si no hay persona, y salio bien. */
    public void registrar(Long empresaId, Long actorId, String accion, String recurso, Long recursoId,
            Map<String, Object> detalle) {
        publicar(empresaId, actorId, accion, recurso, recursoId, true, detalle);
    }

    /** Algo que se intento y no salio: un login o un codigo malos. */
    public void registrarFallo(Long empresaId, Long actorId, String accion, Map<String, Object> detalle) {
        publicar(empresaId, actorId, accion, null, null, false, detalle);
    }

    private void publicar(Long empresaId, Long actorId, String accion, String recurso, Long recursoId, boolean exito,
            Map<String, Object> detalle) {
        HttpServletRequest peticion = peticion();
        String navegador = peticion == null ? null : peticion.getHeader("User-Agent");
        SucesoAuditable suceso = new SucesoAuditable(empresaId, actorId == null ? TipoDeActor.SISTEMA
                : TipoDeActor.USUARIO, actorId, accion, recurso, recursoId, exito,
                detalle == null ? Map.of() : Map.copyOf(detalle),
                peticion == null ? null : peticion.getRemoteAddr(),
                navegador == null || navegador.length() <= LARGO_DEL_NAVEGADOR ? navegador
                        : navegador.substring(0, LARGO_DEL_NAVEGADOR),
                MDC.get(Problemas.ID_DE_PETICION), MDC.get(Problemas.ID_DE_TRAZA),
                LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            eventos.publishEvent(suceso);
        } else {
            transaccion.executeWithoutResult(estado -> eventos.publishEvent(suceso));
        }
    }

    private static HttpServletRequest peticion() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
                ? atributos.getRequest() : null;
    }
}
