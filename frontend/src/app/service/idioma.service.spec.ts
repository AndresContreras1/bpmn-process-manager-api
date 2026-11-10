import { DOCUMENT } from '@angular/common';
import { Component, LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { IdiomaService, MONEDA } from './idioma.service';

@Component({ template: '' })
class Pantalla {}

/** Un documento de mentira: lo que importa es la cookie que queda y a donde se va. */
function documento(pathname: string, protocol: string = 'https:') {
  return {
    cookie: '',
    location: { pathname, search: '?pagina=2', hash: '#arriba', protocol, assign: vi.fn() },
  };
}

function servicio(locale: string, doc: ReturnType<typeof documento>): IdiomaService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: LOCALE_ID, useValue: locale },
      { provide: DOCUMENT, useValue: doc },
      provideRouter([{ path: '**', component: Pantalla }]),
    ],
  });
  return TestBed.inject(IdiomaService);
}

describe('IdiomaService', () => {
  it('guarda la cookie y abre la misma pantalla en la carpeta del idioma nuevo', async () => {
    const doc = documento('/es-CO/procesos/7');
    const idiomas = servicio('es-CO', doc);
    await TestBed.inject(Router).navigateByUrl('/procesos/7?pagina=2#arriba');
    idiomas.ir('fr-FR');

    expect(doc.cookie).toBe('idioma=fr-FR; Path=/; Max-Age=31536000; SameSite=Lax; Secure');
    expect(doc.location.assign).toHaveBeenCalledWith('/fr-FR/procesos/7?pagina=2#arriba');
  });

  it('si el router esta navegando, va a la pantalla de destino y no a la que se esta dejando', async () => {
    const doc = documento('/en-US/login');
    const idiomas = servicio('en-US', doc);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/login');
    const navegando = router.navigateByUrl('/procesos');
    idiomas.ir('es-CO');
    await navegando;

    expect(doc.location.assign).toHaveBeenCalledWith('/es-CO/procesos');
  });

  it('el mismo idioma solo recuerda la cookie', () => {
    const doc = documento('/en-AU/casos', 'http:');
    servicio('en-AU', doc).ir('en-AU');

    expect(doc.cookie).toBe('idioma=en-AU; Path=/; Max-Age=31536000; SameSite=Lax');
    expect(doc.location.assign).not.toHaveBeenCalled();
  });

  it('con ng serve no hay carpetas: guarda la cookie y se queda donde esta', () => {
    const doc = documento('/procesos');
    servicio('en-US', doc).ir('es-CO');

    expect(doc.cookie).toContain('idioma=es-CO');
    expect(doc.location.assign).not.toHaveBeenCalled();
  });

  it('el servidor de desarrollo dice en, que es el ingles de Estados Unidos, y cada idioma tiene su moneda', () => {
    expect(servicio('en', documento('/')).actual).toBe('en-US');
    expect(MONEDA['es-CO']).toBe('COP');
    expect(MONEDA['en-AU']).toBe('AUD');
    expect(MONEDA['fr-FR']).toBe('EUR');
  });
});
