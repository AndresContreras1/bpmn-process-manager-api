package com.facimus.procesos.gestion.service;

import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/** HU-02: alta y administracion de colaboradores. HU-03: inicio de sesion. */
public interface UsuarioService {

    /**
     * Unico punto que da de alta usuarios, tanto colaboradores como el administrador de una empresa nueva.
     * El correo es el usuario del login, que todavia no conoce la empresa: tiene que ser unico en todo el sistema.
     * El autor es quien da el alta; vacio cuando es el registro de la tienda, donde el primer administrador se crea
     * a si mismo y asi figura en el historial. Sin contrasena se genera una temporal, que la respuesta trae una
     * sola vez en claveTemporal (D17).
     */
    UsuarioResponse crearColaborador(Long empresaId, Long autorId, String nombre, String email, String password,
            RolAcceso rolAcceso);

    void validarCorreoDisponible(String email);

    /**
     * El autor es el administrador que hace el cambio: nadie puede desactivar su propia cuenta. Lo que llegue vacio
     * se queda como estaba.
     */
    UsuarioResponse actualizar(Long empresaId, Long autorId, Long usuarioId, String nombre, RolAcceso rolAcceso,
            Boolean activo, Long version);

    void desactivar(Long empresaId, Long autorId, Long usuarioId);

    /**
     * D17: le da al usuario otra contrasena temporal, que se devuelve una sola vez en claveTemporal, y cierra sus
     * sesiones. Quien entre con ella no podra hacer nada mas que cambiarla.
     */
    UsuarioResponse restablecerClave(Long empresaId, Long autorId, Long usuarioId);

    /**
     * PR 35: borra a pedido los datos personales de un usuario, sin vuelta atras. Su fila se queda, porque la nombran
     * la autoria de lo que hizo y el historial, pero con un seudonimo y una direccion que no es suya, desactivada y sin
     * clave que sirva; el historial lo nombra tambien con el seudonimo, y su correo se puede volver a registrar.
     */
    void anonimizar(Long empresaId, Long autorId, Long usuarioId);

    /** Cambia la contrasena propia, comprobando la que esta en uso, y deja de exigir el cambio. */
    UsuarioResponse cambiarClavePropia(Long empresaId, Long usuarioId, String actual, String nueva);

    /** HU-03: las credenciales de un usuario activo para el login; vacio si el correo no existe o esta desactivado. */
    Optional<CredencialesUsuario> buscarCredenciales(String email);

    /**
     * El hash de la misma clave, rehecho por el login con el algoritmo y el costo de hoy. No es un cambio de nadie: no
     * va al historial, no cierra sesiones ni cambia la version del usuario.
     */
    void renovarHash(Long empresaId, Long usuarioId, String hash);

    /**
     * HU-02: una pagina de colaboradores de la tienda. El nombre es una parte, sin distinguir mayusculas, y los
     * desactivados solo salen si se piden: siguen existiendo para poder reactivarlos.
     */
    PageResponse<UsuarioResponse> buscar(Long empresaId, String nombre, boolean incluirInactivos, Pageable pageable);

    UsuarioResponse obtener(Long empresaId, Long usuarioId);
}
