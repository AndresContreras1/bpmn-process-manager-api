package com.facimus.procesos.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.MembresiaRol;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;

/**
 * D13: a que roles de proceso pertenece cada persona. La base impide que la misma pareja se guarde dos veces, que
 * es lo que convierte "reemplazar la lista" en una operacion sin sorpresas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class MembresiaRolRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MembresiaRolRepository membresiaRolRepository;

    private Empresa tienda;
    private Usuario ana;
    private RolProceso ventas;
    private RolProceso bodega;

    @BeforeEach
    void laTiendaSuGenteYSusRoles() {
        tienda = empresa("Tienda de membresias", "900321654-1");
        ana = usuario(tienda, "Ana", "ana@membresias.com");
        ventas = rol(tienda, "Sales");
        bodega = rol(tienda, "Warehouse");
    }

    @Test
    @DisplayName("La misma persona no entra dos veces en el mismo rol")
    void mismaParejaDosVeces_laBaseLaRechaza() {
        membresiaRolRepository.saveAndFlush(membresia(tienda, ana, ventas));

        assertThatThrownBy(() -> membresiaRolRepository.saveAndFlush(membresia(tienda, ana, ventas)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una persona puede estar en varios roles, y un rol tener varias personas")
    void variosRoles_yVariasPersonas() {
        Usuario beto = usuario(tienda, "Beto", "beto@membresias.com");
        membresiaRolRepository.save(membresia(tienda, ana, ventas));
        membresiaRolRepository.save(membresia(tienda, ana, bodega));
        membresiaRolRepository.saveAndFlush(membresia(tienda, beto, ventas));

        assertThat(membresiaRolRepository.rolesDe(tienda.getId(), ana.getId()))
                .containsExactly(ventas.getId(), bodega.getId());
        assertThat(membresiaRolRepository.rolesDe(tienda.getId(), beto.getId()))
                .containsExactly(ventas.getId());
    }

    @Test
    @DisplayName("Los roles de una persona no se ven desde otra tienda")
    void rolesDe_noCruzaTiendas() {
        membresiaRolRepository.saveAndFlush(membresia(tienda, ana, ventas));
        Empresa otra = empresa("Tienda vecina", "900321654-2");

        assertThat(membresiaRolRepository.rolesDe(otra.getId(), ana.getId())).isEmpty();
        assertThat(membresiaRolRepository.findAllByUsuarioIdAndEmpresaIdOrderByIdAsc(ana.getId(), otra.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Borrar las de una persona deja intactas las de las demas")
    void borrarLasDe_soloLasDeEsaPersona() {
        Usuario beto = usuario(tienda, "Beto", "beto@membresias.com");
        membresiaRolRepository.save(membresia(tienda, ana, ventas));
        membresiaRolRepository.save(membresia(tienda, ana, bodega));
        membresiaRolRepository.saveAndFlush(membresia(tienda, beto, ventas));

        assertThat(membresiaRolRepository.borrarLasDe(tienda.getId(), ana.getId())).isEqualTo(2);

        assertThat(membresiaRolRepository.rolesDe(tienda.getId(), ana.getId())).isEmpty();
        assertThat(membresiaRolRepository.rolesDe(tienda.getId(), beto.getId())).containsExactly(ventas.getId());
    }

    private MembresiaRol membresia(Empresa empresa, Usuario usuario, RolProceso rol) {
        return MembresiaRol.builder().empresa(empresa).usuario(usuario).rolProceso(rol).build();
    }

    private Empresa empresa(String nombre, String nit) {
        return em.persistFlushFind(Empresa.builder().nombre(nombre).nit(nit)
                .correoContacto(nit + "@membresias.com").fechaRegistro(LocalDate.now()).build());
    }

    private Usuario usuario(Empresa empresa, String nombre, String email) {
        return em.persistFlushFind(Usuario.builder().empresa(empresa).nombre(nombre).email(email)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv").rolAcceso(RolAcceso.EDITOR).activo(true).build());
    }

    private RolProceso rol(Empresa empresa, String nombre) {
        return em.persistFlushFind(RolProceso.builder().empresa(empresa).nombre(nombre)
                .descripcion("Hace su parte").activo(true).build());
    }
}
