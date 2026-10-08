package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRETO = "clave-de-pruebas-de-al-menos-32-bytes";
    private static final String NUEVO = "clave-nueva-de-la-rotacion-de-32-bytes";
    private static final String EMISOR = "bpmn-process-manager";
    private static final String AUDIENCIA = "bpmn-process-manager-api";

    private final ApiPrincipal editor = new ApiPrincipal(10L, 1L, RolAcceso.EDITOR, "ana@acme.com", "sesion-1", false);

    @Test
    @DisplayName("Un token recien generado es valido y trae la identidad completa, sesion incluida")
    void JwtService_validar_tokenGenerado_devuelveElPrincipal() {
        JwtService jwtService = servicio(SECRETO, 900);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).contains(editor);
    }

    @Test
    @DisplayName("El token dice que clave lo firmo, quien lo emitio, para quien es, y trae un id propio")
    void JwtService_generarToken_llevaKidEmisorAudienciaEId() {
        JwtService jwtService = servicio(SECRETO, 900);
        SecretKey clave = Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));

        Jws<Claims> uno = Jwts.parser().verifyWith(clave).build().parseSignedClaims(jwtService.generarToken(editor));
        Jws<Claims> otro = Jwts.parser().verifyWith(clave).build().parseSignedClaims(jwtService.generarToken(editor));

        assertThat(uno.getHeader().getKeyId()).isEqualTo(JwtService.idDe(clave)).hasSize(16);
        assertThat(uno.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(uno.getPayload().getIssuer()).isEqualTo(EMISOR);
        assertThat(uno.getPayload().getAudience()).containsExactly(AUDIENCIA);
        assertThat(uno.getPayload().getId()).isNotBlank().isNotEqualTo(otro.getPayload().getId());
    }

    @Test
    @DisplayName("Cambiar la empresa dentro del token invalida la firma")
    void JwtService_validar_payloadDeOtraEmpresa_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, 900);
        String[] original = jwtService.generarToken(editor).split("\\.");
        String[] otraEmpresa = jwtService
                .generarToken(new ApiPrincipal(10L, 2L, RolAcceso.EDITOR, "ana@acme.com", "sesion-1", false))
                .split("\\.");

        String manipulado = original[0] + "." + otraEmpresa[1] + "." + original[2];

        assertThat(jwtService.validar(manipulado)).isEmpty();
    }

    @Test
    @DisplayName("Un token pedido por diez minutos vence a los diez minutos, y sin pedir nada, a los de la propiedad")
    void JwtService_generarToken_venceCuandoSeLePide() {
        JwtService jwtService = servicio(SECRETO, 900);
        SecretKey clave = Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8));

        Claims corto = Jwts.parser().verifyWith(clave).build()
                .parseSignedClaims(jwtService.generarToken(editor, Duration.ofMinutes(10))).getPayload();
        Claims normal = Jwts.parser().verifyWith(clave).build()
                .parseSignedClaims(jwtService.generarToken(editor)).getPayload();

        assertThat(corto.getExpiration().getTime() - corto.getIssuedAt().getTime()).isEqualTo(600_000);
        assertThat(normal.getExpiration().getTime() - normal.getIssuedAt().getTime()).isEqualTo(900_000);
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void JwtService_validar_tokenExpirado_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, -1);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void JwtService_validar_firmadoConOtraClave_devuelveVacio() {
        JwtService emisor = servicio("otra-clave-distinta-de-al-menos-32-bytes", 900);
        JwtService receptor = servicio(SECRETO, 900);

        assertThat(receptor.validar(emisor.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Un token emitido para otra audiencia, o por otro emisor, se rechaza aunque la firma sea buena")
    void JwtService_validar_otraAudienciaUOtroEmisor_devuelveVacio() {
        JwtService receptor = servicio(SECRETO, 900);
        JwtService paraOtraApi = new JwtService(SECRETO, "", EMISOR, "otra-api", 900);
        JwtService deOtroEmisor = new JwtService(SECRETO, "", "otro-emisor", AUDIENCIA, 900);

        assertThat(receptor.validar(paraOtraApi.generarToken(editor))).isEmpty();
        assertThat(receptor.validar(deOtroEmisor.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Rotar la clave no cierra sesiones: lo firmado con la anterior vale mientras se acepte, y despues no")
    void JwtService_validar_firmadoConLaClaveAnterior_valeMientrasSeAcepte() {
        String deAntes = servicio(SECRETO, 900).generarToken(editor);
        JwtService durante = new JwtService(NUEVO, SECRETO, EMISOR, AUDIENCIA, 900);
        JwtService despues = servicio(NUEVO, 900);

        assertThat(durante.validar(deAntes)).contains(editor);
        assertThat(despues.validar(deAntes)).isEmpty();
        assertThat(despues.validar(durante.generarToken(editor))).as("durante la rotacion se firma con la nueva")
                .contains(editor);
    }

    @Test
    @DisplayName("Un token sin kid, o con uno que no es de ninguna clave aceptada, se rechaza")
    void JwtService_validar_sinKidOConKidDesconocido_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, 900);

        assertThat(jwtService.validar(firmado("EDITOR", "sesion-1", null))).isEmpty();
        assertThat(jwtService.validar(firmado("EDITOR", "sesion-1", "0123456789abcdef"))).isEmpty();
    }

    @Test
    @DisplayName("Un token sin firma (alg none) se rechaza")
    void JwtService_validar_sinFirma_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, 900);
        String sinFirma = Jwts.builder()
                .header().keyId(kidDeLasPruebas()).and()
                .issuer(EMISOR)
                .audience().add(AUDIENCIA).and()
                .subject("ana@acme.com")
                .claim(JwtService.CLAIM_USUARIO_ID, 10L)
                .claim(JwtService.CLAIM_EMPRESA_ID, 1L)
                .claim(JwtService.CLAIM_ROL, "ADMINISTRADOR")
                .claim(JwtService.CLAIM_SESION, "sesion-1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();

        assertThat(sinFirma).endsWith(".");
        assertThat(jwtService.validar(sinFirma)).isEmpty();
    }

    @Test
    @DisplayName("Un texto que no es JWT se rechaza sin lanzar excepcion")
    void JwtService_validar_textoCualquiera_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, 900);

        assertThat(jwtService.validar("no-es-un-token")).isEmpty();
    }

    @Test
    @DisplayName("Un token bien firmado sin sesion, como los de antes de las sesiones, se rechaza")
    void JwtService_validar_tokenSinSesion_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, 900);

        assertThat(jwtService.validar(firmado("EDITOR", null, kidDeLasPruebas()))).isEmpty();
    }

    @Test
    @DisplayName("Un token bien firmado con un rol que no existe se rechaza")
    void JwtService_validar_rolDesconocido_devuelveVacio() {
        JwtService jwtService = servicio(SECRETO, 900);

        assertThat(jwtService.validar(firmado("SUPERUSUARIO", "sesion-1", kidDeLasPruebas()))).isEmpty();
        assertThat(jwtService.validar(firmado("EDITOR", "sesion-1", kidDeLasPruebas()))).isPresent();
    }

    @Test
    @DisplayName("Sin JWT_SECRET usa una clave aleatoria y los tokens siguen funcionando")
    void JwtService_generarToken_sinSecreto_usaClaveAleatoria() {
        JwtService jwtService = servicio("", 900);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isPresent();
    }

    private static JwtService servicio(String secreto, long segundos) {
        return new JwtService(secreto, "", EMISOR, AUDIENCIA, segundos);
    }

    private static String kidDeLasPruebas() {
        return JwtService.idDe(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)));
    }

    /** Un token firmado con la clave de las pruebas, con los claims que se quieran y el kid que se diga. */
    private static String firmado(String rol, String sesion, String kid) {
        Date ahora = new Date();
        return Jwts.builder()
                .header().keyId(kid).and()
                .issuer(EMISOR)
                .audience().add(AUDIENCIA).and()
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
