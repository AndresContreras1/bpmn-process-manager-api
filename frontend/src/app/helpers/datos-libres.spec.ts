import { aDatos, aPares, comoTexto } from './datos-libres';

describe('datos libres', () => {
  it('lee como numero, booleano, null o JSON lo que lo parece, y lo demas como texto', () => {
    expect(
      aDatos([
        { clave: 'total', valor: '120.5' },
        { clave: 'aprobado', valor: 'true' },
        { clave: 'rechazado', valor: 'false' },
        { clave: 'nota', valor: 'null' },
        { clave: 'items', valor: '[1, 2]' },
        { clave: 'cliente', valor: '{"id": 7}' },
        { clave: 'ciudad', valor: 'Bogota' },
        { clave: 'roto', valor: '{no es json' },
        { clave: 'vacio', valor: '   ' },
      ]),
    ).toEqual({
      total: 120.5,
      aprobado: true,
      rechazado: false,
      nota: null,
      items: [1, 2],
      cliente: { id: 7 },
      ciudad: 'Bogota',
      roto: '{no es json',
      vacio: '',
    });
  });

  it('ignora las filas sin clave y recorta la clave', () => {
    expect(
      aDatos([
        { clave: '  pedido ', valor: '42' },
        { clave: '   ', valor: 'se pierde' },
      ]),
    ).toEqual({ pedido: 42 });
  });

  it('no toma por numero lo que solo empieza como uno', () => {
    expect(aDatos([{ clave: 'codigo', valor: '007a' }])).toEqual({ codigo: '007a' });
  });

  it('vuelve a filas lo que devolvio la API, con el texto tal cual y lo demas en JSON', () => {
    expect(aPares({ ciudad: 'Bogota', total: 3, datos: { a: 1 } })).toEqual([
      { clave: 'ciudad', valor: 'Bogota' },
      { clave: 'total', valor: '3' },
      { clave: 'datos', valor: '{"a":1}' },
    ]);
    expect(aPares(null)).toEqual([]);
  });

  it('ensena undefined como texto vacio', () => {
    expect(comoTexto(undefined)).toBe('');
    expect(comoTexto(null)).toBe('null');
  });
});
