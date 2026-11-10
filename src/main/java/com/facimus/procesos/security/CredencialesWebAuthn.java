package com.facimus.procesos.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.PasskeyGuardada;
import com.facimus.procesos.gestion.service.PasskeyService;

/**
 * PR 49b: las credenciales de WebAuthn de Spring Security, guardadas en la tabla {@code passkeys} con la tienda de su
 * duena, en vez del esquema de Spring, que no la tiene y quedaria fuera de la seguridad por filas y del borrado de
 * tiendas. Solo traduce: lo que se guarda y a quien pertenece lo decide PasskeyService.
 */
@Component
public class CredencialesWebAuthn implements UserCredentialRepository {

    private final PasskeyService passkeys;

    public CredencialesWebAuthn(PasskeyService passkeys) {
        this.passkeys = passkeys;
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return passkeys.buscar(credentialId.getBytes()).map(CredencialesWebAuthn::registro).orElse(null);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return passkeys.delHandle(userId.getBytes()).stream().map(CredencialesWebAuthn::registro).toList();
    }

    /**
     * Spring Security la llama con una credencial recien registrada y despues de cada entrada, con el contador nuevo.
     * Un contador que no sube rechaza la entrada: dos usos de la misma firma, o una copia de la clave.
     */
    @Override
    public void save(CredentialRecord registro) {
        PasskeyGuardada guardada = guardada(registro);
        if (passkeys.buscar(guardada.credencialId()).isEmpty()) {
            passkeys.guardarNueva(guardada);
        } else if (!passkeys.registrarUso(guardada)) {
            throw new IllegalStateException("El contador de firmas de la passkey no subió.");
        }
    }

    @Override
    public void delete(Bytes credentialId) {
        passkeys.borrar(credentialId.getBytes());
    }

    static CredentialRecord registro(PasskeyGuardada guardada) {
        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(new Bytes(guardada.credencialId()))
                .userEntityUserId(new Bytes(guardada.handle()))
                .publicKey(new ImmutablePublicKeyCose(guardada.clavePublica()))
                .signatureCount(guardada.contadorDeFirmas())
                .uvInitialized(guardada.uvInicializado())
                .transports(transportes(guardada.transportes()))
                .backupEligible(guardada.respaldoPosible())
                .backupState(guardada.respaldada())
                .attestationObject(new Bytes(guardada.objetoDeAtestacion()))
                .attestationClientDataJSON(new Bytes(guardada.datosDelCliente()))
                .label(guardada.etiqueta())
                .created(guardada.creadaEn())
                .lastUsed(guardada.ultimoUsoEn())
                .build();
    }

    static PasskeyGuardada guardada(CredentialRecord registro) {
        return new PasskeyGuardada(registro.getCredentialId().getBytes(), registro.getUserEntityUserId().getBytes(),
                registro.getPublicKey().getBytes(), registro.getSignatureCount(), registro.isUvInitialized(),
                registro.getTransports().stream().map(AuthenticatorTransport::getValue).sorted()
                        .collect(Collectors.joining(",")),
                registro.isBackupEligible(), registro.isBackupState(), registro.getAttestationObject().getBytes(),
                registro.getAttestationClientDataJSON().getBytes(), registro.getLabel(), registro.getCreated(),
                registro.getLastUsed());
    }

    private static Set<AuthenticatorTransport> transportes(String guardados) {
        if (guardados == null || guardados.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(guardados.split(",")).map(String::strip).filter(valor -> !valor.isEmpty())
                .map(AuthenticatorTransport::valueOf).collect(Collectors.toSet());
    }
}
