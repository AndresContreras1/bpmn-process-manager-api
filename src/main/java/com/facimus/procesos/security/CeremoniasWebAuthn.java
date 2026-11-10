package com.facimus.procesos.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.response.OpcionesWebAuthnResponse;
import com.facimus.procesos.gestion.service.PasskeyService;
import com.facimus.procesos.gestion.service.PersonaWebAuthn;
import com.facimus.procesos.security.DesafiosWebAuthn.Guardado;
import com.facimus.procesos.security.DesafiosWebAuthn.Proposito;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 49b: las tres ceremonias de WebAuthn (registrar una passkey, verificarla en un login a medias o al confirmar quien
 * es, y entrar sin clave) sobre las operaciones del relying party de Spring Security. No se usa el {@code webAuthn()}
 * del DSL: guarda el desafio en la sesion HTTP, que la API no tiene, y abre la sesion de Spring en vez de las cookies de
 * siempre. Los desafios viven en {@link DesafiosWebAuthn} y las credenciales en la tabla {@code passkeys}.
 *
 * <p>Todo pide la verificacion del dispositivo (huella, rostro o PIN) y una credencial descubrible, la que permite
 * entrar sin escribir el correo. No se pide atestacion: la API no decide que marcas de dispositivo acepta.
 */
@Component
public class CeremoniasWebAuthn {

    private static final Logger log = LoggerFactory.getLogger(CeremoniasWebAuthn.class);

    public static final String NO_VERIFICADA = "La passkey no se pudo verificar: vuelve a intentarlo desde el principio.";

    private static final String DESAFIO_VENCIDO = "El desafío venció o ya se usó: pide unas opciones nuevas.";

    /** ES256 y EdDSA son lo que firma un dispositivo moderno; RS256, lo que firma Windows Hello en equipos viejos. */
    private static final List<PublicKeyCredentialParameters> ALGORITMOS = List.of(PublicKeyCredentialParameters.ES256,
            PublicKeyCredentialParameters.EdDSA, PublicKeyCredentialParameters.RS256);

    private static final AuthenticatorSelectionCriteria SELECCION = AuthenticatorSelectionCriteria.builder()
            .residentKey(ResidentKeyRequirement.REQUIRED)
            .userVerification(UserVerificationRequirement.REQUIRED)
            .build();

    private static final Duration VIGENCIA = DesafiosWebAuthn.VIGENCIA;

    private final Webauthn4JRelyingPartyOperations operaciones;
    private final PublicKeyCredentialRpEntity relyingParty;
    private final DesafiosWebAuthn desafios;
    private final PasskeyService passkeys;
    private final TransactionTemplate transaccion;
    private final JsonMapper json;

    @SuppressWarnings("java:S107") // las piezas de WebAuthn, cada una por su nombre
    public CeremoniasWebAuthn(CredencialesWebAuthn credenciales, PersonasWebAuthn personas, DesafiosWebAuthn desafios,
            PasskeyService passkeys, TransactionTemplate transaccion, @Value("${webauthn.rp-id}") String rpId,
            @Value("${webauthn.rp-nombre}") String rpNombre, @Value("${webauthn.origenes}") Set<String> origenes) {
        this.relyingParty = PublicKeyCredentialRpEntity.builder().id(rpId).name(rpNombre).build();
        this.operaciones = new Webauthn4JRelyingPartyOperations(personas, credenciales, relyingParty, origenes);
        this.operaciones.setCustomizeCreationOptions(opciones -> opciones.pubKeyCredParams(ALGORITMOS)
                .authenticatorSelection(SELECCION)
                .attestation(AttestationConveyancePreference.NONE)
                .timeout(VIGENCIA));
        this.operaciones.setCustomizeRequestOptions(opciones -> opciones
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(VIGENCIA));
        this.desafios = desafios;
        this.passkeys = passkeys;
        this.transaccion = transaccion;
        // Los navegadores agregan campos (authenticatorAttachment, publicKeyAlgorithm...) que la verificacion no usa.
        this.json = JsonMapper.builder()
                .addModule(new WebauthnJacksonModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /** Las opciones para registrar una passkey nueva de la persona, sin las que ya tiene. */
    public OpcionesWebAuthnResponse opcionesDeRegistro(ApiPrincipal principal) {
        PublicKeyCredentialCreationOptions opciones = operaciones.createPublicKeyCredentialCreationOptions(
                new ImmutablePublicKeyCredentialCreationOptionsRequest(autenticacion(principal)));
        PublicKeyCredentialUserEntity persona = opciones.getUser();
        String desafio = desafios.guardar(Proposito.REGISTRO, principal.usuarioId(),
                new Guardado(opciones.getChallenge().toBase64UrlString(), persona.getId().toBase64UrlString(),
                        persona.getName(), persona.getDisplayName(), List.of()));
        return new OpcionesWebAuthnResponse(desafio, json.valueToTree(opciones));
    }

    /**
     * Verifica lo que el dispositivo respondio a las opciones del registro y guarda la passkey. Devuelve el id de la
     * credencial; si algo no verifica, lanza SolicitudInvalidaException.
     */
    public byte[] registrar(ApiPrincipal principal, String desafio, String etiqueta, JsonNode credencial) {
        Guardado guardado = desafios.tomar(desafio, Proposito.REGISTRO, principal.usuarioId())
                .orElseThrow(() -> new SolicitudInvalidaException(DESAFIO_VENCIDO));
        PublicKeyCredential<AuthenticatorAttestationResponse> leida = leer(credencial,
                new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {
                });
        if (passkeys.buscar(leida.getRawId().getBytes()).isPresent()) {
            throw new ReglaNegocioException("Esa passkey ya está registrada.");
        }
        CredentialRecord registrada = verificado(() -> transaccion.execute(estado -> operaciones.registerCredential(
                new ImmutableRelyingPartyRegistrationRequest(creacion(guardado),
                        new RelyingPartyPublicKey(leida, etiqueta == null || etiqueta.isBlank() ? "Passkey"
                                : etiqueta)))));
        return registrada.getCredentialId().getBytes();
    }

    /**
     * Las opciones para que la persona de la sesion firme con una de sus passkeys: para completar el login a medias o
     * para confirmar quien es. Sin passkeys lanza ReglaNegocioException.
     */
    public OpcionesWebAuthnResponse opcionesDeVerificacion(ApiPrincipal principal) {
        PublicKeyCredentialRequestOptions opciones = operaciones.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(autenticacion(principal)));
        if (opciones.getAllowCredentials() == null || opciones.getAllowCredentials().isEmpty()) {
            throw new ReglaNegocioException("No tienes passkeys: usa el código de tu aplicación o uno de "
                    + "recuperación.");
        }
        return opcionesDePeticion(Proposito.VERIFICACION, principal.usuarioId(), opciones);
    }

    /**
     * La persona de la sesion firmo con una de sus passkeys: vacio si no verifica, si el desafio no es suyo o si la
     * passkey es de otra persona.
     */
    public Optional<PersonaWebAuthn> verificar(ApiPrincipal principal, String desafio, JsonNode credencial) {
        return desafios.tomar(desafio, Proposito.VERIFICACION, principal.usuarioId())
                .flatMap(guardado -> autenticar(guardado, credencial))
                .filter(persona -> persona.usuarioId().equals(principal.usuarioId())
                        && persona.empresaId().equals(principal.empresaId()));
    }

    /** Las opciones para entrar sin clave: sin credenciales, porque todavia no se sabe quien es. */
    public OpcionesWebAuthnResponse opcionesDeEntrada() {
        return opcionesDePeticion(Proposito.ENTRADA, null,
                operaciones.createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(null)));
    }

    /** Quien firmo para entrar sin clave, si la firma verifica con una passkey suya. */
    public Optional<PersonaWebAuthn> entrar(String desafio, JsonNode credencial) {
        return desafios.tomar(desafio, Proposito.ENTRADA, null).flatMap(guardado -> autenticar(guardado, credencial));
    }

    /** El id de la credencial que dice traer una respuesta, para la bitacora de un intento fallido. */
    public Optional<byte[]> credencialDe(JsonNode credencial) {
        try {
            return Optional.of(Bytes.fromBase64(credencial.path("rawId").asString()).getBytes());
        } catch (RuntimeException ilegible) {
            return Optional.empty();
        }
    }

    private OpcionesWebAuthnResponse opcionesDePeticion(Proposito proposito, Long usuarioId,
            PublicKeyCredentialRequestOptions opciones) {
        List<PublicKeyCredentialDescriptor> permitidas = opciones.getAllowCredentials() == null ? List.of()
                : opciones.getAllowCredentials();
        String desafio = desafios.guardar(proposito, usuarioId, new Guardado(
                opciones.getChallenge().toBase64UrlString(), null, null, null,
                permitidas.stream().map(descriptor -> descriptor.getId().toBase64UrlString()).toList()));
        return new OpcionesWebAuthnResponse(desafio, json.valueToTree(opciones));
    }

    private Optional<PersonaWebAuthn> autenticar(Guardado guardado, JsonNode credencial) {
        try {
            PublicKeyCredential<AuthenticatorAssertionResponse> leida = leer(credencial,
                    new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {
                    });
            PublicKeyCredentialUserEntity persona = verificado(() -> transaccion.execute(estado ->
                    operaciones.authenticate(new RelyingPartyAuthenticationRequest(peticion(guardado), leida))));
            return passkeys.personaPorHandle(persona.getId().getBytes());
        } catch (SolicitudInvalidaException noVerifica) {
            return Optional.empty();
        }
    }

    /** Lo que firma el dispositivo se rechaza igual sea cual sea el motivo; el motivo queda en el log. */
    private static <T> T verificado(Supplier<T> verificacion) {
        try {
            T resultado = verificacion.get();
            if (resultado == null) {
                throw new SolicitudInvalidaException(NO_VERIFICADA);
            }
            return resultado;
        } catch (SolicitudInvalidaException | ReglaNegocioException propia) {
            throw propia;
        } catch (RuntimeException rechazo) {
            log.debug("Una respuesta de WebAuthn no verificó", rechazo);
            throw new SolicitudInvalidaException(NO_VERIFICADA);
        }
    }

    private <T> T leer(JsonNode credencial, TypeReference<T> tipo) {
        try {
            return json.treeToValue(credencial, json.constructType(tipo));
        } catch (RuntimeException ilegible) {
            throw new SolicitudInvalidaException("La credencial no tiene la forma de PublicKeyCredential.toJSON().");
        }
    }

    /** Las opciones del registro tal como se mandaron: las mismas piezas fijas y lo que se guardo de ellas. */
    private PublicKeyCredentialCreationOptions creacion(Guardado guardado) {
        return PublicKeyCredentialCreationOptions.builder()
                .rp(relyingParty)
                .user(ImmutablePublicKeyCredentialUserEntity.builder()
                        .id(Bytes.fromBase64(guardado.handle()))
                        .name(guardado.nombre())
                        .displayName(guardado.nombreVisible())
                        .build())
                .challenge(Bytes.fromBase64(guardado.desafio()))
                .pubKeyCredParams(ALGORITMOS)
                .authenticatorSelection(SELECCION)
                .attestation(AttestationConveyancePreference.NONE)
                .excludeCredentials(List.of())
                .timeout(VIGENCIA)
                .build();
    }

    private PublicKeyCredentialRequestOptions peticion(Guardado guardado) {
        return PublicKeyCredentialRequestOptions.builder()
                .challenge(Bytes.fromBase64(guardado.desafio()))
                .rpId(relyingParty.getId())
                .allowCredentials(guardado.credenciales().stream()
                        .map(id -> PublicKeyCredentialDescriptor.builder().id(Bytes.fromBase64(id)).build())
                        .toList())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(VIGENCIA)
                .build();
    }

    /** La persona de la sesion como la ve Spring Security: su correo, que es el nombre de usuario de WebAuthn. */
    private static Authentication autenticacion(ApiPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal.email(), null, List.of());
    }
}
