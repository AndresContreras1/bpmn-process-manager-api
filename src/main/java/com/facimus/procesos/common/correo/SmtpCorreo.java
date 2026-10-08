package com.facimus.procesos.common.correo;

import java.nio.charset.StandardCharsets;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * El correo por SMTP, con el servidor que se configure: el cuerpo va en texto y en HTML a la vez, y el cliente de
 * correo muestra el que sabe. Si el servidor no lo acepta, lanza, y la cola lo vuelve a intentar mas tarde.
 */
public class SmtpCorreo implements Correo {

    private final JavaMailSender servidor;
    private final String remitente;

    public SmtpCorreo(JavaMailSender servidor, String remitente) {
        this.servidor = servidor;
        this.remitente = remitente;
    }

    @Override
    public void enviar(CorreoSaliente correo) {
        servidor.send(mensaje -> {
            MimeMessageHelper partes = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            partes.setFrom(remitente);
            partes.setTo(correo.para());
            partes.setSubject(correo.asunto());
            partes.setText(correo.texto(), correo.html());
        });
    }
}
