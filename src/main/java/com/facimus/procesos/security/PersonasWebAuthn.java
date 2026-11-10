package com.facimus.procesos.security;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.PasskeyService;
import com.facimus.procesos.gestion.service.PersonaWebAuthn;

/**
 * PR 49b: las personas tal como las ve WebAuthn. El nombre de usuario es el correo, que es unico en la base y es lo que
 * el dispositivo muestra al elegir la cuenta; el user handle vive en {@code usuarios.webauthn_id}.
 */
@Component
public class PersonasWebAuthn implements PublicKeyCredentialUserEntityRepository {

    private final PasskeyService passkeys;

    public PersonasWebAuthn(PasskeyService passkeys) {
        this.passkeys = passkeys;
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return passkeys.personaPorHandle(id.getBytes()).map(PersonasWebAuthn::entidad).orElse(null);
    }

    /** Sin user handle todavia, no hay entidad: Spring Security crea una al azar y la guarda con {@link #save}. */
    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return passkeys.personaPorCorreo(username).filter(persona -> persona.handle() != null)
                .map(PersonasWebAuthn::entidad).orElse(null);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity entidad) {
        passkeys.asignarHandle(entidad.getName(), entidad.getId().getBytes());
    }

    /** El user handle no se borra: se va con la persona cuando se la anonimiza o se borra su tienda. */
    @Override
    public void delete(Bytes id) {
        // Nada que hacer: la API nunca le pide a Spring Security que borre una persona.
    }

    static PublicKeyCredentialUserEntity entidad(PersonaWebAuthn persona) {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(persona.handle()))
                .name(persona.email())
                .displayName(persona.nombre())
                .build();
    }
}
