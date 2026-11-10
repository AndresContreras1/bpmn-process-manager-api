import tokens from './tokens.json';
import { AA_CONTROL, AA_TEXTO, colorDe, contraste } from './contraste';

describe('contraste de los tokens', () => {
  it('mide como WCAG: negro sobre blanco es 21 y un color contra si mismo, 1', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5);
    expect(contraste('#0f6e56', '#0f6e56')).toBe(1);
    expect(contraste('#ffffff', '#767676')).toBeCloseTo(4.54, 2);
  });

  it('sigue los alias hasta el color concreto', () => {
    expect(colorDe(tokens, 'color.marca.primario')).toBe('#0f6e56');
    expect(() => colorDe(tokens, 'color.verde.950')).toThrow();
  });

  // Cada par de texto y fondo que usa la web, en claro y en oscuro.
  const pares: [string, string, string, number][] = [
    ['texto blanco en un boton primario', '#ffffff', 'color.marca.primario', AA_TEXTO],
    ['un enlace sobre la pagina', 'color.marca.enlace', 'color.fondo.pagina', AA_TEXTO],
    ['un enlace sobre una tarjeta blanca', 'color.marca.enlace', '#ffffff', AA_TEXTO],
    ['un boton secundario sobre la pagina', 'color.marca.secundario', 'color.fondo.pagina', AA_TEXTO],
    ['el texto sobre la pagina', 'color.fondo.texto', 'color.fondo.pagina', AA_TEXTO],
    ['el texto secundario sobre la pagina', 'color.fondo.texto-secundario', 'color.fondo.pagina', AA_TEXTO],
    ['un aviso de exito', 'color.estado.exito', 'color.fondo.pagina', AA_TEXTO],
    ['un aviso de cuidado', 'color.estado.aviso', 'color.fondo.pagina', AA_TEXTO],
    ['un error', 'color.estado.peligro', 'color.fondo.pagina', AA_TEXTO],
    ['una informacion', 'color.estado.info', 'color.fondo.pagina', AA_TEXTO],
    ['el texto en oscuro', 'color.oscuro.texto', 'color.oscuro.fondo', AA_TEXTO],
    ['el texto secundario en oscuro', 'color.oscuro.texto-secundario', 'color.oscuro.fondo', AA_TEXTO],
    ['un enlace en oscuro', 'color.marca.enlace-oscuro', 'color.oscuro.fondo', AA_TEXTO],
    ['un enlace en oscuro sobre una superficie', 'color.marca.enlace-oscuro', 'color.oscuro.superficie', AA_TEXTO],
    ['el borde de un control en oscuro', 'color.oscuro.texto-secundario', 'color.oscuro.superficie', AA_CONTROL],
  ];

  it.each(pares)('%s llega al nivel AA', (_descripcion, texto, fondo, minimo) => {
    const color = (valor: string): string => (valor.startsWith('#') ? valor : colorDe(tokens, valor));
    expect(contraste(color(texto), color(fondo))).toBeGreaterThanOrEqual(minimo);
  });

  it('cada paso de la escala del verde es mas oscuro que el anterior', () => {
    const pasos: string[] = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900'];
    const contrastes: number[] = pasos.map((paso: string) =>
      contraste(colorDe(tokens, `color.verde.${paso}`), '#ffffff'),
    );
    expect([...contrastes].sort((a: number, b: number) => a - b)).toEqual(contrastes);
  });
});
