import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';

import { ESPERA_AL_ESCRIBIR_MS } from '../../helpers/buscador';
import { Persona } from '../../models/comentario.model';
import { ComentarioService } from '../../service/comentario.service';
import { SelectorDeMencionesComponent } from './selector-de-menciones.component';

/**
 * El selector de menciones con su pantalla: la pausa al escribir, que una busqueda nueva deja sin efecto la anterior
 * y que una respuesta tardia no abre la lista. La API es un doble que responde cuando la prueba quiere.
 */
describe('SelectorDeMencionesComponent', () => {
  const ana: Persona = { id: 1, nombre: 'Ana' };
  const andres: Persona = { id: 2, nombre: 'Andres' };
  let pedidas: string[];
  let responder: (texto: string) => Observable<Persona[]>;
  let fixture: ComponentFixture<SelectorDeMencionesComponent>;
  let selector: SelectorDeMencionesComponent;
  let elegidas: number[][];

  beforeEach(() => {
    vi.useFakeTimers();
    pedidas = [];
    elegidas = [];
    responder = () => of([ana, andres]);
    TestBed.configureTestingModule({
      imports: [SelectorDeMencionesComponent],
      providers: [
        {
          provide: ComentarioService,
          useValue: {
            mencionables: (_proceso: number, texto: string) => {
              pedidas.push(texto);
              return responder(texto);
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(SelectorDeMencionesComponent);
    fixture.componentRef.setInput('procesoId', 7);
    selector = fixture.componentInstance;
    selector.cambio.subscribe((ids) => elegidas.push(ids));
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  const campo = (): HTMLInputElement => fixture.nativeElement.querySelector('[data-testid="menciones-campo"]');
  const opciones = (): string[] =>
    Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('[data-testid="mencion-opcion"]')).map((una) =>
      una.textContent!.trim(),
    );

  function escribir(texto: string): void {
    campo().value = texto;
    campo().dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(ESPERA_AL_ESCRIBIR_MS);
    fixture.detectChanges();
  }

  it('busca despues de la pausa al escribir, no con cada tecla', () => {
    selector.enfocar();
    pedidas = [];
    campo().value = 'a';
    campo().dispatchEvent(new Event('input'));
    campo().value = 'an';
    campo().dispatchEvent(new Event('input'));
    expect(pedidas).toEqual([]);

    vi.advanceTimersByTime(ESPERA_AL_ESCRIBIR_MS);
    fixture.detectChanges();

    expect(pedidas).toEqual(['an']);
    expect(opciones()).toEqual(['Ana', 'Andres']);
  });

  it('despues de elegir a alguien, el mismo texto vuelve a buscar y abre la lista', () => {
    selector.enfocar();
    escribir('an');
    fixture.nativeElement.querySelector('[data-testid="mencion-opcion"]').dispatchEvent(new MouseEvent('mousedown'));
    fixture.detectChanges();
    expect(elegidas).toEqual([[1]]);
    expect(opciones()).toEqual([]);

    escribir('an');

    expect(pedidas.filter((texto) => texto === 'an')).toHaveLength(2);
    expect(opciones()).toEqual(['Andres']);
  });

  it('una respuesta vieja no pisa la del texto de ahora', () => {
    const vieja = new Subject<Persona[]>();
    responder = (texto) => (texto === '' ? vieja : of([andres]));
    selector.enfocar();
    escribir('and');

    vieja.next([ana, andres]);
    fixture.detectChanges();

    expect(opciones()).toEqual(['Andres']);
  });

  it('lo escrito antes de elegir no vuelve a abrir la lista cuando termina la pausa', () => {
    selector.enfocar();
    fixture.detectChanges();
    campo().value = 'an';
    campo().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="mencion-opcion"]').dispatchEvent(new MouseEvent('mousedown'));

    vi.advanceTimersByTime(ESPERA_AL_ESCRIBIR_MS);
    fixture.detectChanges();

    expect(elegidas).toEqual([[1]]);
    expect(campo().value).toBe('');
    expect(opciones()).toEqual([]);
  });

  it('una respuesta que llega con el foco ya fuera no abre la lista sobre la pagina', () => {
    const tardia = new Subject<Persona[]>();
    responder = () => tardia;
    selector.enfocar();
    selector.salir();

    tardia.next([ana, andres]);
    fixture.detectChanges();

    expect(opciones()).toEqual([]);
    expect(selector.buscando).toBe(false);
  });

  it('la flecha pregunta ya, sin esperar la pausa', () => {
    selector.enfocar();
    escribir('an');
    selector.elegir(0);
    pedidas = [];
    campo().value = 'and';
    campo().dispatchEvent(new Event('input'));

    selector.teclear(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    fixture.detectChanges();

    expect(pedidas).toEqual(['and']);
    expect(opciones()).toEqual(['Andres']);
  });
});
