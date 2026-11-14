import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { Carpeta } from '../../models/carpeta.model';
import { CarpetaService } from '../../service/carpeta.service';
import { CarpetasComponent } from './carpetas.component';

/** Una pagina OnPush que no cambia nada mientras llegan las carpetas, como el portal cuando su lista ya cargo. */
@Component({
  imports: [CarpetasComponent],
  template: '<app-carpetas [puedeEditar]="false" [seleccionada]="null" />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class PaginaQuieta {}

/**
 * El arbol de carpetas dentro de una pagina OnPush: lo que responde la API se pinta aunque la pagina no haya cambiado.
 * La API es un doble que responde cuando la prueba quiere.
 */
describe('CarpetasComponent', () => {
  const fulfillment: Carpeta = { id: 3, nombre: 'Fulfillment', padreId: null, procesos: 1, version: 0 };
  let respuesta: Subject<Carpeta[]>;
  let fixture: ComponentFixture<PaginaQuieta>;

  beforeEach(() => {
    respuesta = new Subject<Carpeta[]>();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: CarpetaService, useValue: { listar: () => respuesta } }],
    });
    fixture = TestBed.createComponent(PaginaQuieta);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  const pantalla = (): HTMLElement => fixture.nativeElement;

  it('pinta las carpetas que llegan despues de que la pagina ya se pinto', () => {
    expect(pantalla().querySelector("[data-carpeta='Fulfillment']")).toBeNull();

    respuesta.next([fulfillment]);
    fixture.detectChanges();

    expect(pantalla().querySelector("[data-carpeta='Fulfillment']")?.textContent).toContain('Fulfillment');
  });

  it('dice el error de la API aunque la pagina no haya cambiado', () => {
    respuesta.error(new HttpErrorResponse({ status: 500, error: { detail: 'La API no responde.' } }));
    fixture.detectChanges();

    expect(pantalla().querySelector('#carpetas-error')).not.toBeNull();
  });
});
