package com.facimus.procesos.gestion.service;

import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;

/** HU-02: alta y administracion de colaboradores. HU-03: inicio de sesion. */
public interface UsuarioService {

    /**
     * Unico punto que da de alta usuarios, tanto colaboradores como el administrador de una empresa nueva.
     * El correo es el usuario del login, que todavia no conoce la empresa: tiene que ser unico en todo el sistema.
     * El autor es quien da el alta; vacio cuando es el registro de la tienda, donde el primer administrador se crea
     * a si mismo y asi figura en el historial.
     */
    UsuarioResponse crearColaborador(Long empresaId, Long autorId, String nombre, String email, String password,
            RolAcceso rolAcceso);

    void validarCorreoDisponible(String email);

    /** El autor es el administrador que hace el cambio: nadie puede desactivar su propia cuenta. */
    UsuarioResponse actualizar(Long empresaId, Long autorId, Long usuarioId, RolAcceso rolAcceso, Boolean activo,
            Long version);

    void desactivar(Long empresaId, Long autorId, Long usuarioId);

    /** HU-03: las credenciales de un usuario activo para el login; vacio si el correo no existe o esta desactivado. */
    Optional<CredencialesUsuario> buscarCredenciales(String email);

    PageResponse<UsuarioResponse> buscar(Long empresaId, Pageable pageable);

    UsuarioResponse obtener(Long empresaId, Long usuarioId);
}
