import { aBytes, aTexto, crearCredencial, fueCancelada, firmar } from './webauthn';

describe('webauthn', () => {
  const bytes = (...valores: number[]): ArrayBuffer => new Uint8Array(valores).buffer;

  it('pasa de base64url a bytes y de vuelta, con y sin relleno', () => {
    expect(aTexto(bytes(251, 255, 0, 1))).toBe('-_8AAQ');
    expect(new Uint8Array(aBytes('-_8AAQ'))).toEqual(new Uint8Array([251, 255, 0, 1]));
    expect(aTexto(aBytes('aGVsbG8'))).toBe('aGVsbG8');
  });

  it('registra con las opciones de la API y devuelve la credencial en la forma de Spring Security', async () => {
    const crear = vi.fn().mockResolvedValue({
      id: 'AQI',
      rawId: bytes(1, 2),
      type: 'public-key',
      authenticatorAttachment: 'platform',
      getClientExtensionResults: () => ({ credProps: { rk: true } }),
      response: { clientDataJSON: bytes(3), attestationObject: bytes(4), getTransports: () => ['internal'] },
    });
    vi.stubGlobal('navigator', { credentials: { create: crear } });

    const credencial = await crearCredencial({
      challenge: 'AAE',
      user: { id: 'AgM', name: 'ana@acme.test' },
      excludeCredentials: [{ id: 'BAU', type: 'public-key' }],
    });

    const publicKey = crear.mock.calls[0][0].publicKey;
    expect(new Uint8Array(publicKey.challenge)).toEqual(new Uint8Array([0, 1]));
    expect(new Uint8Array(publicKey.user.id)).toEqual(new Uint8Array([2, 3]));
    expect(publicKey.user.name).toBe('ana@acme.test');
    expect(new Uint8Array(publicKey.excludeCredentials[0].id)).toEqual(new Uint8Array([4, 5]));
    expect(credencial).toEqual({
      id: 'AQI',
      rawId: 'AQI',
      type: 'public-key',
      response: { clientDataJSON: 'Aw', attestationObject: 'BA', transports: ['internal'] },
      clientExtensionResults: { credProps: { rk: true } },
      authenticatorAttachment: 'platform',
    });
    vi.unstubAllGlobals();
  });

  it('firma con las opciones de la API, sin credenciales permitidas cuando es una entrada sin usuario', async () => {
    const obtener = vi.fn().mockResolvedValue({
      id: 'AQI',
      rawId: bytes(1, 2),
      type: 'public-key',
      authenticatorAttachment: 'platform',
      getClientExtensionResults: () => ({}),
      response: { clientDataJSON: bytes(3), authenticatorData: bytes(5), signature: bytes(6), userHandle: bytes(7) },
    });
    vi.stubGlobal('navigator', { credentials: { get: obtener } });

    const firma = await firmar({ challenge: 'AAE', rpId: 'localhost' });

    expect(obtener.mock.calls[0][0].publicKey.allowCredentials).toEqual([]);
    expect(firma['response']).toEqual({
      clientDataJSON: 'Aw',
      authenticatorData: 'BQ',
      signature: 'Bg',
      userHandle: 'Bw',
    });
    vi.unstubAllGlobals();
  });

  it('cerrar el dialogo del navegador no es un error que haya que mostrar', () => {
    expect(fueCancelada(new DOMException('cerrado', 'NotAllowedError'))).toBe(true);
    expect(fueCancelada(new Error('otro'))).toBe(false);
  });
});
