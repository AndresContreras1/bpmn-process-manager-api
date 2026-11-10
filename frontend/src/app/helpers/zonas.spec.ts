import { zonasHorarias } from './zonas';

describe('zonasHorarias', () => {
  it('trae las del navegador, UTC y las guardadas, sin repetir y en orden', () => {
    const zonas: string[] = zonasHorarias('America/Bogota', 'Antarctica/Troll', null);

    expect(zonas).toContain('UTC');
    expect(zonas).toContain('America/Bogota');
    expect(zonas).toContain('Antarctica/Troll');
    expect(new Set(zonas).size).toBe(zonas.length);
    expect([...zonas].sort((a, b) => a.localeCompare(b))).toEqual(zonas);
  });
});
