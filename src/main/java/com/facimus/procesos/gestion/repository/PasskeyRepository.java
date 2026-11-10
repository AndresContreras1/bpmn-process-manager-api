package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Passkey;

public interface PasskeyRepository extends RepositorioTenant<Passkey> {

    List<Passkey> findAllByUsuarioIdAndEmpresaIdOrderByCreadaEnAscIdAsc(Long usuarioId, Long empresaId);

    Optional<Passkey> findByIdAndUsuarioIdAndEmpresaId(Long id, Long usuarioId, Long empresaId);

    long countByUsuarioIdAndEmpresaId(Long usuarioId, Long empresaId);

    boolean existsByUsuarioIdAndEmpresaId(Long usuarioId, Long empresaId);

    /**
     * Entrar sin clave empieza sin saber de que tienda es: el id de la credencial es lo unico que llega, y es unico
     * en toda la base. Quien la llama lo hace como sistema y lee de ella la tienda.
     */
    Optional<Passkey> findByCredencialId(byte[] credencialId);

    /**
     * Lo que cambia cada uso: el contador de firmas, si el dispositivo esta respaldado y cuando se uso. El contador no
     * baja nunca: de dos usos a la vez con la misma firma, el segundo no cambia nada.
     */
    @Modifying
    @Query("""
            update Passkey p set p.contadorDeFirmas = :contador, p.respaldada = :respaldada, p.uvInicializado = :uv,
                                 p.ultimoUsoEn = :usada
            where p.id = :id and (p.contadorDeFirmas < :contador or (p.contadorDeFirmas = 0 and :contador = 0))
            """)
    int registrarUso(Long id, long contador, boolean respaldada, boolean uv, LocalDateTime usada);

    @Modifying
    @Query("delete from Passkey p where p.empresa.id = :empresaId and p.usuario.id = :usuarioId")
    int borrarLasDe(Long empresaId, Long usuarioId);
}
