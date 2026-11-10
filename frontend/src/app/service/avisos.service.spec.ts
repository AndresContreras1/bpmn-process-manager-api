import { AvisosService, DURACION_DE_UN_AVISO } from './avisos.service';

describe('AvisosService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('un aviso de exito o de informacion se va solo; uno de error se queda hasta que lo cierran', () => {
    const avisos = new AvisosService();
    avisos.exito('Guardado.');
    avisos.info('Hay una version nueva.');
    avisos.error('No se pudo publicar.');
    expect(avisos.avisos().map((aviso) => aviso.tipo)).toEqual(['exito', 'info', 'error']);

    vi.advanceTimersByTime(DURACION_DE_UN_AVISO);
    expect(avisos.avisos().map((aviso) => aviso.texto)).toEqual(['No se pudo publicar.']);

    avisos.cerrar(avisos.avisos()[0].id);
    expect(avisos.avisos()).toEqual([]);
  });

  it('cada aviso tiene su id, aunque digan lo mismo', () => {
    const avisos = new AvisosService();
    avisos.error('Otra vez.');
    avisos.error('Otra vez.');
    const [uno, otro] = avisos.avisos();
    expect(uno.id).not.toBe(otro.id);
  });
});
