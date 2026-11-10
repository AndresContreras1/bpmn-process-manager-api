/**
 * F14: de las opciones en JSON que manda la API a navigator.credentials, y de vuelta. WebAuthn trabaja con bytes y la
 * API con base64url; la forma de la credencial que se devuelve es la que lee Spring Security: id, rawId, type,
 * response, clientExtensionResults y authenticatorAttachment.
 */

type Json = Record<string, unknown>;

/** Si este navegador puede usar passkeys. */
export function soportaPasskeys(): boolean {
  return typeof window !== 'undefined' && 'PublicKeyCredential' in window && !!navigator.credentials;
}

/** Que la persona cerro el dialogo del navegador o no eligio nada: no es un error que haya que mostrar. */
export function fueCancelada(error: unknown): boolean {
  return error instanceof DOMException && (error.name === 'NotAllowedError' || error.name === 'AbortError');
}

/** Registra una passkey con las opciones de creacion; devuelve la credencial lista para la API. */
export async function crearCredencial(opciones: Json): Promise<Json> {
  const usuario = opciones['user'] as Json;
  const publicKey = {
    ...opciones,
    challenge: aBytes(opciones['challenge'] as string),
    user: { ...usuario, id: aBytes(usuario['id'] as string) },
    excludeCredentials: credenciales(opciones['excludeCredentials']),
  } as unknown as PublicKeyCredentialCreationOptions;
  const credencial = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential;
  const respuesta = credencial.response as AuthenticatorAttestationResponse;
  return {
    id: credencial.id,
    rawId: aTexto(credencial.rawId),
    type: credencial.type,
    response: {
      clientDataJSON: aTexto(respuesta.clientDataJSON),
      attestationObject: aTexto(respuesta.attestationObject),
      transports: typeof respuesta.getTransports === 'function' ? respuesta.getTransports() : [],
    },
    clientExtensionResults: credencial.getClientExtensionResults(),
    authenticatorAttachment: credencial.authenticatorAttachment,
  };
}

/** Firma con una passkey las opciones de una verificacion o de una entrada; devuelve la firma lista para la API. */
export async function firmar(opciones: Json): Promise<Json> {
  const publicKey = {
    ...opciones,
    challenge: aBytes(opciones['challenge'] as string),
    allowCredentials: credenciales(opciones['allowCredentials']),
  } as unknown as PublicKeyCredentialRequestOptions;
  const credencial = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential;
  const respuesta = credencial.response as AuthenticatorAssertionResponse;
  return {
    id: credencial.id,
    rawId: aTexto(credencial.rawId),
    type: credencial.type,
    response: {
      clientDataJSON: aTexto(respuesta.clientDataJSON),
      authenticatorData: aTexto(respuesta.authenticatorData),
      signature: aTexto(respuesta.signature),
      userHandle: respuesta.userHandle ? aTexto(respuesta.userHandle) : null,
    },
    clientExtensionResults: credencial.getClientExtensionResults(),
    authenticatorAttachment: credencial.authenticatorAttachment,
  };
}

function credenciales(lista: unknown): Json[] {
  return ((lista as Json[] | undefined) ?? []).map((credencial: Json) => ({
    ...credencial,
    id: aBytes(credencial['id'] as string),
  }));
}

export function aBytes(base64url: string): ArrayBuffer {
  const base64: string = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const binario: string = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) {
    bytes[i] = binario.charCodeAt(i);
  }
  return bytes.buffer;
}

export function aTexto(bytes: ArrayBuffer): string {
  let binario: string = '';
  new Uint8Array(bytes).forEach((byte: number) => (binario += String.fromCharCode(byte)));
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
