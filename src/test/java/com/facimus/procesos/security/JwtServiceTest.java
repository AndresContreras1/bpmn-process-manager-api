package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRETO = "clave-de-pruebas-de-al-menos-32-bytes";

    private final ApiPrincipal editor = new ApiPrincipal(10L, 1L, RolAcceso.EDITOR, "ana@acme.com", "sesion-1", false);

    @Test
    @DisplayName("Un token recien generado es valido y trae la identidad completa, sesion incluida")
    void JwtService_validar_tokenGenerado_devuelveElPrincipal() {
        JwtService jwtService = new JwtService(SECRETO, 900);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).contains(editor);
    }

    @Test
    @DisplayName("Cambiar la empresa dentro del token invalida la firma")
    void JwtService_validar_payloadDeOtraEmpresa_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, 900);
        String[] original = jwtService.generarToken(editor).split("\\.");
        String[] otraEmpresa = jwtService
                .generarToken(new ApiPrincipal(10L, 2L, RolAcceso.EDITOR, "ana@acme.com", "sesion-1", false))
                .split("\\.");

        String manipulado = original[0] + "." + otraEmpresa[1] + "." + original[2];

        assertThat(jwtService.validar(manipulado)).isEmpty();
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void JwtService_validar_tokenExpirado_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, -1);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void JwtService_validar_firmadoConOtraClave_devuelveVacio() {
        JwtService emisor = new JwtService("otra-clave-distinta-de-al-menos-32-bytes", 900);
        JwtService receptor = new JwtService(SECRETO, 900);

        assertThat(receptor.validar(emisor.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Un texto que no es JWT se rechaza sin lanzar excepcion")
    void JwtService_validar_textoCualquiera_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, 900);

        assertThat(jwtService.validar("no-es-un-token")).isEmpty();
    }

    @Test
    @DisplayName("Un token bien firmado sin sesion, como los de antes de las sesiones, se rechaza")
    void JwtService_validar_tokenSinSesion_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, 900);

        assertThat(jwtService.validar(firmado("EDITOR", null))).isEmpty();
    }

    @Test
    @DisplayName("Un token bien firmado con un rol que no existe se rechaza")
    void JwtService_validar_rolDesconocido_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, 900);

        assertThat(jwtService.validar(firmado("SUPERUSUARIO", "sesion-1"))).isEmpty();
        assertThat(jwtService.validar(firmado("EDITOR", "sesion-1"))).isPresent();
    }

    @Test
    @DisplayName("Sin JWT_SECRET usa una clave aleatoria y los tokens siguen funcionando")
    void JwtService_generarToken_sinSecreto_usaClaveAleatoria() {
        JwtService jwtService = new JwtService("", 900);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isPresent();
    }

    /** Un token firmado con la clave de las pruebas, con los claims que se quieran. */
    private static String firmado(String rol, String sesion) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject("ana@acme.com")
                .claim(JwtService.CLAIM_USUARIO_ID, 10L)
                .claim(JwtService.CLAIM_EMPRESA_ID, 1L)
                .claim(JwtService.CLAIM_ROL, rol)
                .claim(JwtService.CLAIM_SESION, sesion)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
