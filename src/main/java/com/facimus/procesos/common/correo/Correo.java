package com.facimus.procesos.common.correo;

/**
 * El puerto por el que sale el correo. En la aplicacion lo implementa un cliente SMTP generico, porque todos los
 * proveedores hablan SMTP; quien opere el producto elige el suyo con las variables {@code SMTP_*}. Lanzar es no haberlo
 * mandado: quien lo pidio por la cola lo vuelve a intentar.
 */
public interface Correo {

    void enviar(CorreoSaliente correo);
}
