package com.facimus.procesos.gestion.model;

import java.time.Duration;

/**
 * Para que sirve un enlace del correo, cuanto vive, a que pantalla de la web lleva y con que plantilla se escribe. La
 * recuperacion vive poco porque abre una cuenta; la invitacion, una semana, porque la persona invitada puede tardar.
 */
public enum PropositoDeEnlace {

    VERIFICAR_CORREO(Duration.ofHours(48), "/verificar-correo", "verificar-correo"),
    RECUPERAR_CLAVE(Duration.ofMinutes(15), "/restablecer-clave", "recuperar-clave"),
    INVITACION(Duration.ofDays(7), "/aceptar-invitacion", "invitacion");

    private final Duration vigencia;
    private final String pantalla;
    private final String plantilla;

    PropositoDeEnlace(Duration vigencia, String pantalla, String plantilla) {
        this.vigencia = vigencia;
        this.pantalla = pantalla;
        this.plantilla = plantilla;
    }

    public Duration vigencia() {
        return vigencia;
    }

    /** La ruta de la web que recibe el token del enlace. */
    public String pantalla() {
        return pantalla;
    }

    public String plantilla() {
        return plantilla;
    }
}
