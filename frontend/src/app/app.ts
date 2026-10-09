import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http'
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('buscaativa-web');

  private http = inject(HttpClient)

  status = signal('carregando...');

  constructor(){
    this.http.get<{ status: string }>('/api/ping')
    .subscribe(resposta => this.status.set(resposta.status));
  }
}
