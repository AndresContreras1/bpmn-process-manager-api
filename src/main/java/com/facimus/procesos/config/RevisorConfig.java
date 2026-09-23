package com.facimus.procesos.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP con el que se llama al revisor de IA. Lleva tiempo de espera propio: una llamada que no vuelve
 * dejaria colgada la peticion del usuario y, con ella, un hilo y una conexion. Se arma aparte del resto: esta API
 * no habla con ningun otro servicio, asi que no hay un cliente compartido que configurar.
 */
@Configuration
public class RevisorConfig {

    @Bean
    public RestClient clienteDelRevisor(@Value("${gemini.base-url}") String baseUrl,
            @Value("${gemini.timeout}") Duration timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica).build();
    }
}
