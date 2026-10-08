package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.model.Empresa;

import jakarta.persistence.LockModeType;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);

    /** HU-23: la empresa con la que se comparte un proceso se busca por su NIT. */
    Optional<Empresa> findByNit(String nit);

    /**
     * Bloquea la fila de la tienda hasta el final de la transaccion (select ... for update). Los cambios que pueden
     * dejarla sin administrador pasan por aqui y se hacen uno detras de otro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id = :id")
    Optional<Empresa> bloquear(Long id);

    /** Vacio si la tienda no esta dada de baja. Lo pregunta cada peticion que cambia algo, asi que lee una columna. */
    @Query("select e.bajaSolicitadaEn from Empresa e where e.id = :id")
    Optional<LocalDateTime> bajaSolicitadaEn(Long id);

    /** Las tiendas cuya baja se pidio antes de ese momento y todavia no se borraron. */
    @Query("select e.id from Empresa e where e.bajaSolicitadaEn < :limite and e.borradaEn is null order by e.id")
    List<Long> conLaBajaAntesDe(LocalDateTime limite);
}
