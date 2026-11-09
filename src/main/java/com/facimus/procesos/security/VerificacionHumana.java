package com.facimus.procesos.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PR 32: Cloudflare Turnstile en el registro de una tienda, si la instalacion lo quiere. Con
 * {@code TURNSTILE_SECRET} vacio no se pide nada. Con el, el registro trae en {@code X-Turnstile-Token} lo que el
 * widget de la web le dio, y aqui se pregunta a Cloudflare si es bueno, para esa IP. Si Cloudflare no contesta, no se
 * deja pasar: cerrar es lo seguro cuando la instalacion pidio comprobarlo.
 */
@Component
public class VerificacionHumana {

    public static final String CABECERA = "X-Turnstile-Token";

    record Respuesta(boolean success, @JsonProperty("error-codes") List<String> errores) {
    }

    private final String secreto;
    private final String url;
    private final RestClient cliente;

    public VerificacionHumana(@Value("${turnstile.secreto}") String secreto, @Value("${turnstile.url}") String url) {
        this.secreto = secreto;
        this.url = url;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(5000);
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    public boolean estaActiva() {
        return StringUtils.hasText(secreto);
    }

    /** Si el token del widget es bueno para esta IP. Sin Turnstile configurado, siempre. */
    public boolean esHumano(String token, String ip) {
        if (!estaActiva()) {
            return true;
        }
        if (!StringUtils.hasText(token) || token.length() > 2048) {
            return false;
        }
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("secret", secreto);
        formulario.add("response", token);
        formulario.add("remoteip", ip);
        try {
            Respuesta respuesta = cliente.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario).retrieve().body(Respuesta.class);
            return respuesta != null && respuesta.success();
        } catch (RestClientException e) {
            return false;
        }
    }
}
