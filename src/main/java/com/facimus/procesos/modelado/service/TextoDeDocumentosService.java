package com.facimus.procesos.modelado.service;

/**
 * PR 65: el texto de cada archivo subido, para la busqueda. Lo lee la cola despues de que el antivirus lo da por bueno,
 * nunca antes.
 */
public interface TextoDeDocumentosService {

    /**
     * El trabajo de la cola: lee el texto de una version disponible que todavia no lo tiene y lo guarda. Un archivo que
     * no se puede leer queda con la fecha y sin texto, y se busca solo por su nombre: no es un trabajo fallido.
     */
    void leer(Long empresaId, Long versionId);
}
