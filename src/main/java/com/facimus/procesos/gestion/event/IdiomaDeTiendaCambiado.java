package com.facimus.procesos.gestion.event;

/**
 * PR 65: se publica, dentro de su transaccion, cuando una tienda cambia el idioma de su organizacion. Lo escucha el
 * modulo de modelado para rehacer los vectores de busqueda con las raices del idioma nuevo.
 */
public record IdiomaDeTiendaCambiado(Long empresaId, String idioma) {
}
