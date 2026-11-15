import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';

import { Documento } from '../../../models/documento.model';
import { DocumentoService } from '../../../service/documento.service';
import { PanelElementoComponent } from './panel-elemento.component';

function documento(cambios: Partial<Documento> = {}): Documento {
  return {
    id: 5,
    procesoId: 7,
    bpmnId: 'Task_1',
    nombre: 'packing.txt',
    versionActual: 1,
    estado: 'DISPONIBLE',
    motivo: null,
    revisado: true,
    tipo: 'text/plain',
    tamano: 120,
    version: 0,
    fechaModificacion: '2026-11-12T10:00:00',
    ...cambios,
  };
}

/** El panel de lo que se lee de un elemento, pintado: su titulo y lo que pasa cuando el enlace de un documento falla. */
describe('PanelElementoComponent', () => {
  let fixture: ComponentFixture<PanelElementoComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PanelElementoComponent],
      providers: [
        {
          provide: DocumentoService,
          useValue: { enlace: () => throwError(() => new HttpErrorResponse({ status: 404 })) },
        },
      ],
    });
    fixture = TestBed.createComponent(PanelElementoComponent);
  });

  const titulo = (): string => fixture.nativeElement.querySelector('#portal-panel-elemento').textContent.trim();

  it('un elemento sin nombre se titula con su id, como lo anuncia la pagina', () => {
    fixture.componentRef.setInput('elemento', { id: 'Task_1', nombre: '' });
    fixture.detectChanges();

    expect(titulo()).toBe('Task_1');
  });

  it('sin elemento habla del proceso entero', () => {
    fixture.detectChanges();

    expect(titulo()).toBe('The whole process');
  });

  it('si el enlace de un documento falla, lo dice en el panel', () => {
    fixture.componentRef.setInput('elemento', { id: 'Task_1', nombre: 'Wrap the order' });
    fixture.componentRef.setInput('documentos', [documento()]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#portal-documentos-error')).toBeNull();

    fixture.nativeElement.querySelector('[data-testid="portal-documento-bajar"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#portal-documentos-error')).not.toBeNull();
  });
});
