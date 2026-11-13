package com.facimus.procesos.gestion.model;

/** PR 68: si una version se publico o solo se armo para probar el borrador. */
public enum TipoVersion {

    /** La que sale de publicar: tiene numero, puede ser la vigente y no se borra nunca. */
    PUBLICADA,

    /**
     * La instantanea del borrador con la que se prueba sin publicar: su numero es 0, nace retirada para que nunca sea
     * la vigente, ninguna consulta de versiones la ve y la purga se la lleva con sus casos.
     */
    PRUEBA
}
