package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private final UsuarioResponse ana = new UsuarioResponse(10L, "Ana", "ana@acme.com", RolAcceso.EDITOR, true, 1L);

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    @DisplayName("HU-03: la cuenta de un usuario activo lleva su correo, el hash de su clave y su rol")
    void loadUserByUsername_usuarioActivo_devuelveSuCuenta() {
        given(usuarioService.buscarCredenciales("ana@acme.com"))
                .willReturn(Optional.of(new CredencialesUsuario(ana, "hash-de-la-clave")));

        UserDetails cuenta = userAccountService.loadUserByUsername("ana@acme.com");

        assertThat(cuenta.getUsername()).isEqualTo("ana@acme.com");
        assertThat(cuenta.getPassword()).isEqualTo("hash-de-la-clave");
        assertThat(cuenta.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("EDITOR");
        assertThat(((UserAccount) cuenta).usuario()).isEqualTo(ana);
    }

    @Test
    @DisplayName("HU-03: un correo sin usuario activo no tiene cuenta")
    void loadUserByUsername_sinUsuarioActivo_lanzaUsernameNotFound() {
        given(usuarioService.buscarCredenciales("nadie@acme.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userAccountService.loadUserByUsername("nadie@acme.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Al terminar el login, borrar las credenciales saca el hash de la cuenta")
    void eraseCredentials_sacaElHashDeLaCuenta() {
        UserAccount cuenta = new UserAccount(ana, "hash-de-la-clave");

        cuenta.eraseCredentials();

        assertThat(cuenta.getPassword()).isNull();
        assertThat(cuenta.usuario()).isEqualTo(ana);
    }
}
