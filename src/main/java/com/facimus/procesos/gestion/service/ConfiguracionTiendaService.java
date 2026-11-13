package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.model.PoliticaSegundoFactor;

/** D16: lo que cada tienda decide sobre si misma, y la regla que de ahi sale (R-46). */
public interface ConfiguracionTiendaService {

    /**
     * Crea la configuracion de una tienda recien registrada, con lo que trae de fabrica. F12: con el idioma y la zona
     * horaria con que empieza a trabajar su gente.
     */
    void crearPara(Long empresaId, String idioma, String zonaHoraria);

    ConfiguracionTiendaResponse obtener(Long empresaId);

    /**
     * Un modo, unos parametros o unos limites nulos dejan los que tenia: el cuerpo de la edicion crecio y los clientes
     * de antes no los mandan. Los parametros, en cambio, se mandan enteros o no se mandan.
     */
    default ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            ModoSimulacion modo, ParametrosSimulacion parametros, LimitesDeSesion sesiones, Long version) {
        return editar(empresaId, usuarioId, politica, modo, parametros, sesiones, null, null, version);
    }

    /**
     * PR 49 y PR 50: lo mismo con la politica de segundo factor y con la entrada solo por un proveedor; nulas dejan
     * las que tenia.
     */
    @SuppressWarnings("java:S107") // cada parte de la configuracion, por su nombre
    default ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            ModoSimulacion modo, ParametrosSimulacion parametros, LimitesDeSesion sesiones,
            PoliticaSegundoFactor segundoFactor, Boolean soloEntradaExterna, Long version) {
        return editar(empresaId, usuarioId, politica, modo, parametros, sesiones, segundoFactor, soloEntradaExterna,
                null, version);
    }

    /** PR 70: lo mismo con la politica de completar por otro; nula deja la que tenia. */
    @SuppressWarnings("java:S107") // cada parte de la configuracion, por su nombre
    ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            ModoSimulacion modo, ParametrosSimulacion parametros, LimitesDeSesion sesiones,
            PoliticaSegundoFactor segundoFactor, Boolean soloEntradaExterna, Boolean administradorCompletaPorOtro,
            Long version);

    /** PR 70: si en esta tienda un administrador completa la tarea que tiene otra persona. */
    boolean administradorCompletaPorOtro(Long empresaId);

    /** La edicion de la simulacion, que no toca las sesiones. */
    default ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            ModoSimulacion modo, ParametrosSimulacion parametros, Long version) {
        return editar(empresaId, usuarioId, politica, modo, parametros, LimitesDeSesion.SIN_CAMBIOS, version);
    }

    /** D8: en que tick va la simulacion de esta tienda. */
    int reloj(Long empresaId);

    /** Mueve el reloj de la tienda y devuelve el tick al que llego. */
    int avanzarReloj(Long empresaId, int ticks);

    /** Las tiendas que pidieron que su reloj corriera solo, que son las que el trabajo mueve. */
    List<Long> tiendasEnAutomatico();

    /**
     * R-46: comprueba que ese usuario puede crear o editar pools y lanes en su tienda. Lo llama el modelado, que no
     * conoce ni la configuracion ni los roles de acceso.
     */
    void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId);

    /** PR 60: la politica de la tienda es lo que hay por defecto; una concesion de modelar ese proceso la supera. */
    void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId, Long procesoId);
}
