import { HttpErrorResponse } from '@angular/common/http';

import { abrirDescarga, mensajeDeSubida, tamanoLegible } from './archivos';

function error(status: number, cuerpo: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: cuerpo });
}

describe('archivos', () => {
  afterEach(() => vi.restoreAllMocks());

  it('abre el enlace firmado como descarga y no deja el enlace en la pagina', () => {
    const pulsado = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.getAttribute('href')).toBe('/api/v1/descargas/documentos/1/2?firma=x');
      expect(this.getAttribute('download')).toBe('');
      expect(this.isConnected).toBe(true);
    });

    abrirDescarga('/api/v1/descargas/documentos/1/2?firma=x');

    expect(pulsado).toHaveBeenCalledTimes(1);
    expect(document.querySelector('a[download]')).toBeNull();
  });

  it('escribe el tamano en bytes, KB o MB', () => {
    expect(tamanoLegible(812, 'en-US')).toBe('812 B');
    expect(tamanoLegible(48 * 1024, 'en-US')).toBe('48 KB');
    expect(tamanoLegible(3.24 * 1024 * 1024, 'en-US')).toBe('3.2 MB');
    expect(tamanoLegible(3.24 * 1024 * 1024, 'es-CO')).toBe('3,2 MB');
  });

  it('explica una subida rechazada con el detalle de la API, o con uno propio si NGINX corto antes', () => {
    expect(mensajeDeSubida(error(413, { detail: 'La organización llegó a su espacio para documentos.' }))).toBe(
      'La organización llegó a su espacio para documentos.',
    );
    expect(mensajeDeSubida(error(413, '<html>413 Request Entity Too Large</html>'))).toContain('larger than 25 MB');
    expect(mensajeDeSubida(error(415, { detail: 'Ese tipo de archivo no se acepta.' }))).toBe(
      'Ese tipo de archivo no se acepta.',
    );
    expect(mensajeDeSubida(error(415))).toBe('That type of file is not accepted.');
    expect(mensajeDeSubida(error(403))).toBe('Your role does not allow this action.');
  });
});
