import { Component, ChangeDetectionStrategy } from '@angular/core';
import { EstructuraComponent } from './components/estructura/estructura.component';

@Component({
  selector: 'app-root',
  imports: [EstructuraComponent],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class AppComponent {}
