package com.facimus.procesos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.SmtpCorreo;

/**
 * Por donde sale el correo. Con {@code SMTP_HOST}, por ese servidor; sin el, la aplicacion arranca igual y cada
 * correo falla al salir: queda en la cola, se reintenta y termina fallido con el motivo, a la vista en el runbook.
 * Ningun correo se pierde en silencio ni se finge enviado.
 */
@Configuration
public class CorreoConfig {

    private static final Logger log = LoggerFactory.getLogger(CorreoConfig.class);

    @Bean
    public Correo correo(ObjectProvider<JavaMailSender> servidor, @Value("${spring.mail.host:}") String host,
            @Value("${correo.remitente}") String remitente) {
        JavaMailSender smtp = servidor.getIfAvailable();
        if (smtp == null || !StringUtils.hasText(host)) {
            log.warn("Sin servidor de correo (SMTP_HOST): los correos esperan en la cola y fallan al salir.");
            return correo -> {
                throw new IllegalStateException("No hay servidor de correo configurado (SMTP_HOST).");
            };
        }
        return new SmtpCorreo(smtp, remitente);
    }
}
