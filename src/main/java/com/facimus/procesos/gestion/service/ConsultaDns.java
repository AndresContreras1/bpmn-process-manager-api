package com.facimus.procesos.gestion.service;

import java.util.List;

/** PR 50: los registros TXT de un dominio, para verificar que la tienda lo controla. */
public interface ConsultaDns {

    /** Vacio si el dominio no existe o no publica ninguno; los registros de varios trozos llegan unidos. */
    List<String> registrosTxt(String dominio);
}
