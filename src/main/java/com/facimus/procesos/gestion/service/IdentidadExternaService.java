package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.IdentidadExternaResponse;
import com.facimus.procesos.gestion.dto.response.ResultadoEntradaExterna;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/**
 * PR 50: las cuentas de Google o Microsoft de las personas. Una identidad entra si ya esta vinculada; un correo que
 * ya tiene cuenta se vincula solo si su dueno lo confirma; uno de un dominio verificado se une a su tienda.
 */
public interface IdentidadExternaService {

    /**
     * Lo que pasa con quien vuelve del proveedor con ese sub y ese correo. Un correo que el proveedor no da por
     * verificado no vincula ni crea nada.
     */
    ResultadoEntradaExterna entrar(String proveedor, String sujeto, String email, boolean correoVerificado,
            String nombre);

    /** De quien es un vinculo pendiente, si sigue vigente. */
    Optional<UsuarioResponse> usuarioDelVinculo(String token);

    /** Gasta el vinculo y deja la identidad vinculada; uno gastado o vencido lanza EnlaceNoValidoException. */
    void vincular(String token);

    List<IdentidadExternaResponse> identidades(Long empresaId, Long usuarioId);

    /** Quitar la ultima de alguien que solo puede entrar con un proveedor lanza ReglaNegocioException. */
    void desvincular(Long empresaId, Long usuarioId, Long identidadId);

    /** Si la tienda le exige a este rol entrar con un proveedor: a todos salvo a los administradores. */
    boolean exigeEntradaExterna(Long empresaId, RolAcceso rol);

    /** Lo que se borra al anonimizar a una persona. */
    void borrarDe(Long empresaId, Long usuarioId);
}
