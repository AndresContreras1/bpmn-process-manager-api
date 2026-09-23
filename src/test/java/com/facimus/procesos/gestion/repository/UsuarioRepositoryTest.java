package com.facimus.procesos.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

/**
 * Las consultas de usuarios contra la base: las que se acotan a una tienda y las dos del login, que no la conocen
 * todavia. El conteo de administradores activos es el que sostiene la regla de no dejar la tienda sin administrador.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class UsuarioRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Empresa tienda;
    private Empresa otraTienda;

    @BeforeEach
    void crearLasDosTiendas() {
        tienda = empresa("Demo Store");
        otraTienda = empresa("Partner 3PL");
    }

    @Test
    @DisplayName("El conteo de administradores solo cuenta los activos de esa tienda")
    void contarAdministradores_soloLosActivosDeLaTienda() {
        guardar(usuario(tienda, "ada@demo.com", RolAcceso.ADMINISTRADOR, true));
        guardar(usuario(tienda, "bruno@demo.com", RolAcceso.ADMINISTRADOR, true));
        guardar(usuario(tienda, "carla@demo.com", RolAcceso.ADMINISTRADOR, false));
        guardar(usuario(tienda, "dario@demo.com", RolAcceso.EDITOR, true));
        guardar(usuario(otraTienda, "elena@partner.com", RolAcceso.ADMINISTRADOR, true));

        assertThat(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(tienda.getId(),
                RolAcceso.ADMINISTRADOR)).isEqualTo(2);
        assertThat(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(tienda.getId(),
                RolAcceso.SOLO_LECTURA)).isZero();
        assertThat(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(otraTienda.getId(),
                RolAcceso.ADMINISTRADOR)).isEqualTo(1);
    }

    @Test
    @DisplayName("Buscar por correo dentro de la tienda no encuentra al usuario de otra")
    void buscarPorCorreo_conTienda_noCruzaTiendas() {
        Usuario propio = guardar(usuario(tienda, "ada@demo.com", RolAcceso.EDITOR, true));
        Usuario ajeno = guardar(usuario(otraTienda, "elena@partner.com", RolAcceso.EDITOR, true));

        assertThat(usuarioRepository.findByEmpresaIdAndEmail(tienda.getId(), "ada@demo.com")).contains(propio);
        assertThat(usuarioRepository.findByEmpresaIdAndEmail(tienda.getId(), "elena@partner.com")).isEmpty();
        assertThat(usuarioRepository.findByEmpresaIdAndEmail(otraTienda.getId(), "elena@partner.com")).contains(ajeno);
    }

    @Test
    @DisplayName("HU-01: el login busca por correo en todo el sistema, porque todavia no sabe de que tienda es")
    void buscarPorCorreo_sinTienda_encuentraEnCualquiera() {
        Usuario ajeno = guardar(usuario(otraTienda, "elena@partner.com", RolAcceso.ADMINISTRADOR, true));

        assertThat(usuarioRepository.findByEmail("elena@partner.com")).contains(ajeno);
        assertThat(usuarioRepository.existsByEmail("elena@partner.com")).isTrue();
        assertThat(usuarioRepository.existsByEmail("nadie@demo.com")).isFalse();
    }

    @Test
    @DisplayName("HU-02: el listado pagina los usuarios activos de la tienda, en el orden pedido")
    void listar_soloActivosDeLaTienda_enElOrdenPedido() {
        guardar(usuario(tienda, "bruno@demo.com", RolAcceso.EDITOR, true));
        guardar(usuario(tienda, "ada@demo.com", RolAcceso.ADMINISTRADOR, true));
        guardar(usuario(tienda, "carla@demo.com", RolAcceso.EDITOR, false));
        guardar(usuario(otraTienda, "elena@partner.com", RolAcceso.EDITOR, true));

        var pagina = usuarioRepository.findAllByEmpresaIdAndActivoTrue(tienda.getId(),
                PageRequest.of(0, 10, Sort.by("email")));

        assertThat(pagina.getContent()).extracting(Usuario::getEmail).containsExactly("ada@demo.com",
                "bruno@demo.com");
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    private Usuario guardar(Usuario usuario) {
        return em.persistFlushFind(usuario);
    }

    private Empresa empresa(String nombre) {
        return em.persistFlushFind(Empresa.builder()
                .nombre(nombre)
                .nit(Math.abs(nombre.hashCode()) + "-1")
                .correoContacto("contacto@" + nombre.replace(" ", "").toLowerCase() + ".com")
                .fechaRegistro(LocalDate.now())
                .build());
    }

    private static Usuario usuario(Empresa empresa, String email, RolAcceso rolAcceso, boolean activo) {
        return Usuario.builder()
                .empresa(empresa)
                .nombre(email.substring(0, email.indexOf('@')))
                .email(email)
                .passwordHash("hash-de-la-clave")
                .rolAcceso(rolAcceso)
                .activo(activo)
                .build();
    }
}
