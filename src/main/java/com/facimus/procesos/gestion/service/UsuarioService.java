package com.facimus.procesos.gestion.service;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;

/** HU-02: alta y administracion de colaboradores. HU-03: inicio de sesion. */
public interface UsuarioService {

    /**
     * Unico punto que da de alta usuarios, tanto colaboradores como el administrador de una empresa nueva.
     * El correo es el usuario del login, que todavia no conoce la empresa: tiene que ser unico en todo el sistema.
     */
    UsuarioResponse crearColaborador(Long empresaId, String nombre, String email, String password,
            RolAcceso rolAcceso);

    void validarCorreoDisponible(String email);

    UsuarioResponse actualizar(Long empresaId, Long usuarioId, RolAcceso rolAcceso, Boolean activo);

    void desactivar(Long empresaId, Long usuarioId);

    UsuarioResponse autenticar(String email, String password);

    PageResponse<UsuarioResponse> buscar(Long empresaId, Pageable pageable);

    UsuarioResponse obtener(Long empresaId, Long usuarioId);
}
