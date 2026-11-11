package com.facimus.procesos.common.api;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.facimus.procesos.common.PrecondicionFallidaException;

/**
 * PR 40: la version optimista de un recurso, tambien en HTTP. Un GET de algo editable lleva {@code ETag}, y una
 * edicion puede mandar {@code If-Match} junto a la {@code version} del cuerpo: si no coinciden, 412. Vale para todo
 * record con un componente {@code version}, asi que un recurso nuevo la tiene sin que nadie se acuerde.
 */
@ControllerAdvice
public class VersionEnCabeceras extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {

    private static final Map<Class<?>, Optional<RecordComponent>> COMPONENTES = new ConcurrentHashMap<>();

    @Override
    public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {
        return tipo instanceof Class<?> clase && componente(clase).isPresent();
    }

    @Override
    public Object afterBodyRead(Object cuerpo, HttpInputMessage mensaje, MethodParameter parametro, Type tipo,
            Class<? extends HttpMessageConverter<?>> conversor) {
        String ifMatch = mensaje.getHeaders().getFirst(HttpHeaders.IF_MATCH);
        Long version = version(cuerpo);
        if (ifMatch != null && version != null && !coincide(ifMatch, version)) {
            throw new PrecondicionFallidaException("If-Match no coincide con la versión de la petición.",
                    Map.of("versionDelCuerpo", version));
        }
        return cuerpo;
    }

    @Override
    public boolean supports(MethodParameter parametro, Class<? extends HttpMessageConverter<?>> conversor) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object cuerpo, MethodParameter parametro, MediaType tipo,
            Class<? extends HttpMessageConverter<?>> conversor, ServerHttpRequest peticion,
            ServerHttpResponse respuesta) {
        if (HttpMethod.GET.equals(peticion.getMethod()) && cuerpo != null
                && respuesta.getHeaders().getETag() == null) {
            Long version = version(cuerpo);
            if (version != null) {
                respuesta.getHeaders().setETag("\"" + version + "\"");
            }
        }
        return cuerpo;
    }

    /** {@code *} vale siempre; lo que no sea un numero no habla de la version, y no se compara. */
    private static boolean coincide(String ifMatch, long version) {
        String valor = EtiquetasHttp.valor(ifMatch);
        return "*".equals(valor) || !EtiquetasHttp.esNumero(valor) || valor.equals(Long.toString(version));
    }

    private static Long version(Object cuerpo) {
        Optional<RecordComponent> componente = componente(cuerpo.getClass());
        if (componente.isEmpty()) {
            return null;
        }
        try {
            return (Long) componente.get().getAccessor().invoke(cuerpo);
        } catch (ReflectiveOperationException noSeLeyo) {
            throw new IllegalStateException("No se pudo leer la version de " + cuerpo.getClass(), noSeLeyo);
        }
    }

    private static Optional<RecordComponent> componente(Class<?> clase) {
        return COMPONENTES.computeIfAbsent(clase, tipo -> !tipo.isRecord() ? Optional.empty()
                : Arrays.stream(tipo.getRecordComponents())
                        .filter(uno -> "version".equals(uno.getName()) && uno.getType() == Long.class)
                        .findFirst());
    }
}
