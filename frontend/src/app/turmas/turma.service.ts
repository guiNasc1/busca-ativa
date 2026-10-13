import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { Turma, TurmaRequest } from "./turma.model";
import { inject, Injectable } from "@angular/core";

@Injectable({ providedIn: 'root' })
export class TurmaService {

    private http = inject(HttpClient);
    private readonly url = '/api/turmas';

    listar(): Observable<Turma[]> {
        return this.http.get<Turma[]>(this.url);
    }

    buscar(id: number): Observable<Turma> {
        return this.http.get<Turma>(`${this.url}/${id}`);
    }
    
    criar(dados: TurmaRequest): Observable<Turma> {
        return this.http.post<Turma>(this.url, dados)
    }

    atualizar(id: number, dados: TurmaRequest): Observable<Turma> {
        return this.http.put(`${this.url}/${id}`, dados);
    }

    excluir(id:number): Observable<Turma> {
        return this.http.delete<Turma>(`${this.url}/${id}`).;
    }

}