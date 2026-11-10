package com.facimus.procesos.gestion.service;

import java.time.LocalDateTime;
import java.util.Optional;

import com.facimus.procesos.gestion.dto.response.EmpresaResponse;

/** HU-01: registro de empresa + usuario administrador inicial. */
public interface EmpresaService {

    /** Una organizacion en espanol de Colombia y en la hora de Bogota. */
    default EmpresaResponse registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin) {
        return registrar(nombre, nit, correoContacto, nombreAdmin, emailAdmin, passwordAdmin, Idiomas.POR_DEFECTO,
                Idiomas.zonaPorDefecto(Idiomas.POR_DEFECTO));
    }

    /** F12: con el idioma y la zona horaria con que empieza a trabajar su gente, ya validados. */
    @SuppressWarnings("java:S107") // cada dato del registro, por su nombre
    EmpresaResponse registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin, String idioma, String zonaHoraria);

    /** Para quien la pide solo existe su propia empresa: cualquier otro id responde 404. */
    EmpresaResponse obtener(Long empresaId, Long id);

    /**
     * PR 35: da de baja la tienda. Durante 30 dias queda en solo lectura y la baja se puede cancelar; despues se borra
     * todo lo suyo. La confirmacion es el nombre de la tienda, escrito otra vez.
     */
    EmpresaResponse pedirBaja(Long empresaId, Long autorId, String confirmacion);

    /** Cancela una baja mientras dura la gracia: la tienda vuelve a ser como era. */
    EmpresaResponse cancelarBaja(Long empresaId, Long autorId);

    /** Cuando se borrara la tienda, si esta dada de baja; vacio si sigue abierta. */
    Optional<LocalDateTime> borradoProgramado(Long empresaId);

    /** La purga nocturna: borra las tiendas que cumplieron la gracia de su baja. Devuelve cuantas borro. */
    int borrarLasDadasDeBaja();
}
