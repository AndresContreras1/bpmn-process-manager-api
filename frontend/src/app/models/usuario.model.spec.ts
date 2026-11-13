import { inicioDe } from './usuario.model';

describe('inicioDe', () => {
  it('quien solo lee empieza en el portal de lo publicado', () => {
    expect(inicioDe('SOLO_LECTURA')).toBe('/portal');
  });

  it('quien construye procesos empieza en la lista de procesos', () => {
    expect(inicioDe('EDITOR')).toBe('/procesos');
    expect(inicioDe('ADMINISTRADOR')).toBe('/procesos');
  });
});
