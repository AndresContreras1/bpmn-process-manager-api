package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;

/**
 * Como gestion consigue el diagrama de un proceso para publicarlo. El diagrama vive en modelado, que depende de
 * gestion y no al reves: gestion declara lo que necesita y modelado lo implementa, igual que con UsoDeRoles.
 */
public interface InstantaneaDelModelo {

    /** El modelo vivo del proceso, tal como esta ahora. El proceso llega ya leido: quien llama acaba de cargarlo. */
    Instantanea tomar(Long empresaId, ProcesoResponse proceso);

    /**
     * PR 67: la huella de una definicion ya guardada, la de una version publicada, rehecha con la misma forma canonica
     * con que se calcula al publicar. Si no es la que guarda la version, el diagrama cambio despues de publicarse.
     */
    String huellaDe(String definicion);
}
