package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.dto.response.EmpresaResponse;

/** HU-01: registro de empresa + usuario administrador inicial. */
public interface EmpresaService {

    EmpresaResponse registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin);
}
