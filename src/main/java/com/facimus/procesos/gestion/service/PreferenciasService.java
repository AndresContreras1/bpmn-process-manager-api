package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.dto.response.PreferenciasResponse;

/**
 * F12: el idioma y la zona horaria. La organizacion tiene los suyos y cada persona puede elegir otros; lo que no elige,
 * lo toma de su organizacion.
 */
public interface PreferenciasService {

    PreferenciasResponse obtener(Long empresaId, Long usuarioId);

    /** Los de la persona; uno vacio vuelve a seguir el de su organizacion. Una zona que no existe lanza 400. */
    PreferenciasResponse cambiarLasPropias(Long empresaId, Long usuarioId, String idioma, String zonaHoraria);

    /** Los de la organizacion, para quien no eligio otros. Los dos son obligatorios. */
    PreferenciasResponse cambiarLasDeLaOrganizacion(Long empresaId, Long autorId, String idioma, String zonaHoraria);
}
