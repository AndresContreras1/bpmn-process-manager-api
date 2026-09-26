package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;

/** La configuracion de una tienda: una fila por empresa, creada con ella. */
public interface ConfiguracionTiendaRepository extends RepositorioTenant<ConfiguracionTienda> {

    Optional<ConfiguracionTienda> findByEmpresaId(Long empresaId);

    /**
     * Solo el tick, sin traer la fila entera. Se pregunta por cada linea que se escribe en la bitacora de un caso,
     * y lo unico que hace falta ahi es el numero.
     */
    @Query("select c.reloj from ConfiguracionTienda c where c.empresa.id = :empresaId")
    Optional<Integer> relojDe(@Param("empresaId") Long empresaId);

    /** Las tiendas que pidieron que su reloj corriera solo; normalmente ninguna, asi que solo trae los ids. */
    @Query("""
            select c.empresa.id from ConfiguracionTienda c
            where c.modoSimulacion = com.facimus.procesos.gestion.model.ModoSimulacion.AUTOMATICO
            order by c.empresa.id
            """)
    List<Long> empresasEnAutomatico();
}
