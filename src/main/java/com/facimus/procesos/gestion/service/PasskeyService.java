package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.gestion.dto.response.PasskeyRegistradaResponse;
import com.facimus.procesos.gestion.dto.response.PasskeyResponse;

/**
 * PR 49b: las passkeys de cada persona. La verificacion criptografica es de la capa de seguridad, con WebAuthn; aqui
 * vive lo que se guarda de cada una, a quien pertenece y lo que pasa en la cuenta cuando se agrega o se quita una.
 *
 * <p>Las busquedas por el id de la credencial o por el user handle no reciben la tienda: al entrar sin clave es lo
 * unico que llega, y los dos son unicos en toda la base. Corren con el alcance de la peticion, que en esa ruta es el
 * del sistema.
 */
public interface PasskeyService {

    /** Las passkeys de la persona, de la mas vieja a la mas nueva. */
    List<PasskeyResponse> listar(Long empresaId, Long usuarioId);

    /** Le cambia el nombre con que la persona la reconoce. */
    PasskeyResponse renombrar(Long empresaId, Long usuarioId, Long passkeyId, String etiqueta);

    /**
     * La quita. Si era su ultimo segundo factor, sus codigos de recuperacion se van con ella y el login vuelve a la
     * clave sola.
     */
    void quitar(Long empresaId, Long usuarioId, Long passkeyId);

    /**
     * Lo que pasa en la cuenta cuando una passkey nueva quedo guardada: el historial, la bitacora y, si era el primer
     * segundo factor, los diez codigos de recuperacion.
     */
    PasskeyRegistradaResponse registrada(Long empresaId, Long usuarioId, byte[] credencialId);

    /** La persona de un correo, si esta activa, con su user handle si ya tiene uno. */
    Optional<PersonaWebAuthn> personaPorCorreo(String email);

    /** La persona de un user handle, si sigue activa. */
    Optional<PersonaWebAuthn> personaPorHandle(byte[] handle);

    /** El user handle de una persona que todavia no tenia: se crea con su primera passkey y no cambia. */
    void asignarHandle(String email, byte[] handle);

    Optional<PasskeyGuardada> buscar(byte[] credencialId);

    /** Las passkeys de un user handle. */
    List<PasskeyGuardada> delHandle(byte[] handle);

    /** Guarda una passkey recien verificada. Su duena es la persona del user handle. */
    void guardarNueva(PasskeyGuardada passkey);

    /**
     * Lo que cambia cada vez que se usa: el contador de firmas, el respaldo y la hora. Dice si se guardo; un contador
     * que no sube no se guarda.
     */
    boolean registrarUso(PasskeyGuardada passkey);

    /** Quita una passkey por el id de su credencial, sea de quien sea. */
    void borrar(byte[] credencialId);
}
