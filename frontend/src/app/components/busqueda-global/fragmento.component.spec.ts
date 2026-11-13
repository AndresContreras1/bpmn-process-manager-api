import { TestBed } from '@angular/core/testing';

import { FragmentoComponent } from './fragmento.component';

describe('FragmentoComponent', () => {
  function pintar(segmentos: { texto: string; resaltado: boolean }[]): HTMLElement {
    TestBed.configureTestingModule({ imports: [FragmentoComponent] });
    const fixture = TestBed.createComponent(FragmentoComponent);
    fixture.componentRef.setInput('segmentos', segmentos);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('un script o una etiqueta en el texto se leen como texto y no se interpretan', () => {
    const elemento = pintar([
      { texto: 'Before <script>alert(1)</script> and ', resaltado: false },
      { texto: '<img src=x onerror=alert(2)>', resaltado: true },
    ]);

    expect(elemento.querySelector('script')).toBeNull();
    expect(elemento.querySelector('img')).toBeNull();
    expect(elemento.textContent).toContain('<script>alert(1)</script>');
    expect(elemento.textContent).toContain('<img src=x onerror=alert(2)>');
  });

  it('un & se ve como &, sin volverse una entidad ni comerse lo que sigue', () => {
    const elemento = pintar([{ texto: 'Fish & chips &amp; more', resaltado: false }]);

    expect(elemento.textContent?.trim()).toBe('Fish & chips &amp; more');
  });

  it('solo lo resaltado va marcado, en el orden en que llego', () => {
    const elemento = pintar([
      { texto: 'Use ', resaltado: false },
      { texto: 'reinforced', resaltado: true },
      { texto: ' tape', resaltado: false },
    ]);

    const marcas = Array.from(elemento.querySelectorAll('mark')).map((marca) => marca.textContent);
    expect(marcas).toEqual(['reinforced']);
    expect(elemento.textContent?.replace(/\s+/g, ' ').trim()).toBe('Use reinforced tape');
  });
});
