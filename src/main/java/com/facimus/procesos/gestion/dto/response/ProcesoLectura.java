package com.facimus.procesos.gestion.dto.response;

/**
 * Un proceso abierto por la puerta de lectura (HU-23): puede ser propio o compartido por otra empresa. Quien arma su
 * diagrama consulta los elementos con la empresa duena, no con la del usuario.
 */
public record ProcesoLectura(ProcesoResponse proceso, Long empresaPropietariaId, boolean compartido) {
}
