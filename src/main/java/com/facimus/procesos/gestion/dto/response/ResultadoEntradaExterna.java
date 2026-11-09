package com.facimus.procesos.gestion.dto.response;

/**
 * PR 50: lo que decide la API de quien vuelve de un proveedor. Entra con la cuenta vinculada o recien creada por un
 * dominio verificado; espera a confirmar el vinculo con la cuenta que ya tenia ese correo, con el token que lo
 * confirma; o no entra, con un motivo que la web traduce.
 */
public record ResultadoEntradaExterna(Tipo tipo, UsuarioResponse usuario, String token, String motivo) {

    public enum Tipo {
        ENTRA,
        VINCULAR,
        RECHAZADA
    }

    public static ResultadoEntradaExterna entra(UsuarioResponse usuario) {
        return new ResultadoEntradaExterna(Tipo.ENTRA, usuario, null, null);
    }

    public static ResultadoEntradaExterna vincular(String token) {
        return new ResultadoEntradaExterna(Tipo.VINCULAR, null, token, null);
    }

    public static ResultadoEntradaExterna rechazada(String motivo) {
        return new ResultadoEntradaExterna(Tipo.RECHAZADA, null, null, motivo);
    }
}
