# Guia 02 — CRUD de Turma (ponta a ponta)

> Objetivo: cadastrar, listar, editar e excluir **Turmas**, passando por todas as camadas do backend e do frontend.
> É o "molde" que vamos repetir para Aluno, Responsável, etc. Entendendo este, os outros são cópia com ajustes.

---

## 0. O caminho de uma requisição

```
 ANGULAR                                          SPRING BOOT                                     BANCO
 ┌──────────────┐  ┌──────────────┐   HTTP    ┌────────────┐  ┌──────────┐  ┌────────────┐   ┌─────────┐
 │ Componente   │→ │ TurmaService │ ───JSON──►│ Controller │→ │ Service  │→ │ Repository │ → │ tabela  │
 │ (tela)       │← │ (HttpClient) │ ◄──JSON── │ (REST)     │← │ (regras) │← │ (JPA)      │ ← │ turma   │
 └──────────────┘  └──────────────┘           └────────────┘  └──────────┘  └────────────┘   └─────────┘
                                                 ▲   DTO (record)  │  Entity (@Entity)
```

| Camada            | Responsabilidade                                    | No seu JSF era…                     |
|-------------------|-----------------------------------------------------|-------------------------------------|
| **Entity**        | Espelho da tabela                                   | a mesma `@Entity`                   |
| **Repository**    | Acesso ao banco (CRUD + consultas)                  | `AbstractFacade` + `EntityManager`  |
| **Service**       | Regras de negócio + transação                       | lógica espalhada no bean/facade     |
| **DTO**           | O "formato" do JSON que entra e sai                 | (não existia — a tela lia a entidade)|
| **Controller**    | Recebe HTTP, valida, chama o service, responde      | Managed bean (action methods)       |
| **Componente**    | Tela + estado da tela                               | xhtml + managed bean                |
| **Service (front)** | Fala com a API                                    | (não existia)                       |

Estrutura de pacotes que vamos criar no backend:

```
com.escola.buscaativa
├── controller/   TurmaController, PingController
├── dto/          TurmaRequest, TurmaResponse
├── exception/    RecursoNaoEncontradoException, RegraNegocioException, GlobalExceptionHandler
├── model/        Turma, Turno
├── repository/   TurmaRepository
└── service/      TurmaService
```

> **Organização alternativa:** muita gente organiza por *funcionalidade* (`turma/` com tudo de turma dentro). Por *camada* fica mais fácil de enxergar o padrão enquanto você aprende; dá para reorganizar depois.

---

# PARTE A — Backend

## A1. Enum `Turno`

`model/Turno.java`:

```java
package com.escola.buscaativa.model;

public enum Turno {
    MANHA, TARDE, NOITE, INTEGRAL
}
```

## A2. Entidade `Turma`

`model/Turma.java`:

```java
package com.escola.buscaativa.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "turma")
@Getter
@Setter
@NoArgsConstructor
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // id gerado pelo banco (serial/identity)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nome;                                  // ex.: "1º Ano A"

    @Enumerated(EnumType.STRING)                          // grava "MANHA", não 0
    @Column(nullable = false, length = 10)
    private Turno turno;

    @Column(nullable = false)
    private Integer anoLetivo;                            // vira coluna ano_letivo
}
```

Pontos para fixar:
- **Nada mudou em relação ao JPA que você já usava.** O Spring só configura o Hibernate por você.
- `@Enumerated(EnumType.STRING)`: **sempre** use `STRING`. O padrão (`ORDINAL`) grava 0, 1, 2… e quebra se alguém reordenar o enum.
- O Spring Boot converte `anoLetivo` → `ano_letivo` automaticamente (estratégia de nomes *snake_case*).
- Lombok conforme a regra do Guia 01: `@Getter @Setter @NoArgsConstructor`, nunca `@Data` em entidade.

✅ **Checkpoint:** rode o backend. Como `ddl-auto=update`, o Hibernate cria a tabela. No log aparece `create table turma (...)`. Confira no **Supabase → Table Editor**.

## A3. Repository — adeus, AbstractFacade

`repository/TurmaRepository.java`:

```java
package com.escola.buscaativa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.escola.buscaativa.model.Turma;

public interface TurmaRepository extends JpaRepository<Turma, Long> {

    List<Turma> findAllByOrderByAnoLetivoDescNomeAsc();

    Optional<Turma> findByNomeIgnoreCaseAndAnoLetivo(String nome, Integer anoLetivo);
}
```

**É só uma interface.** Não tem implementação: o Spring Data cria uma em tempo de execução.

- `JpaRepository<Turma, Long>` (entidade, tipo do id) já te dá: `save`, `findById`, `findAll`, `delete`, `count`, `existsById`, paginação… Tudo o que o seu `AbstractFacade` fazia.
- **Query derivada do nome do método**: o Spring lê o nome e monta o JPQL.

  | Nome do método                            | JPQL gerado (aproximado)                                      |
  |-------------------------------------------|---------------------------------------------------------------|
  | `findAllByOrderByAnoLetivoDescNomeAsc()`   | `select t from Turma t order by t.anoLetivo desc, t.nome asc` |
  | `findByNomeIgnoreCaseAndAnoLetivo(n, a)`  | `... where upper(t.nome) = upper(:n) and t.anoLetivo = :a`    |

  Errou o nome de um campo? **A aplicação nem sobe**, e o erro diz qual campo. Para consultas complexas existe `@Query("select ...")`, que veremos depois.
- `Optional<Turma>`: o retorno "pode não existir". Força você a tratar o caso de não encontrar, em vez de receber `null`.

## A4. DTOs com `record`

Por que não devolver a entidade direto no JSON?
1. Você controla **exatamente** o que entra e sai (o cliente não manda `id` no cadastro, por exemplo).
2. Mudanças na tabela não quebram o contrato da API.
3. Evita serializar relacionamentos *lazy* (erro clássico quando houver `Turma → alunos`).

`dto/TurmaRequest.java` — o que **chega** (POST/PUT):

```java
package com.escola.buscaativa.dto;

import com.escola.buscaativa.model.Turno;

import jakarta.validation.constraints.*;

public record TurmaRequest(

        @NotBlank(message = "Informe o nome da turma")
        @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres")
        String nome,

        @NotNull(message = "Informe o turno")
        Turno turno,

        @NotNull(message = "Informe o ano letivo")
        @Min(value = 2000, message = "Ano letivo inválido")
        @Max(value = 2100, message = "Ano letivo inválido")
        Integer anoLetivo
) {}
```

`dto/TurmaResponse.java` — o que **sai**:

```java
package com.escola.buscaativa.dto;

import com.escola.buscaativa.model.Turma;
import com.escola.buscaativa.model.Turno;

public record TurmaResponse(Long id, String nome, Turno turno, Integer anoLetivo) {

    // "construtor de conversão": Entity -> DTO
    public static TurmaResponse from(Turma turma) {
        return new TurmaResponse(turma.getId(), turma.getNome(), turma.getTurno(), turma.getAnoLetivo());
    }
}
```

**`record` em 30 segundos:** uma classe imutável onde você declara só os campos. O Java gera construtor, *accessors* (`nome()`, **sem** `get`), `equals`, `hashCode` e `toString`. É o substituto natural do Lombok para DTOs.

> As anotações de validação são o **mesmo Bean Validation** que o JSF usava. A diferença é quem dispara a validação: aqui é o `@Valid` no controller (A7).

## A5. Exceções da aplicação

`exception/RecursoNaoEncontradoException.java`:

```java
package com.escola.buscaativa.exception;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
```

`exception/RegraNegocioException.java`:

```java
package com.escola.buscaativa.exception;

public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
```

São `RuntimeException` (não checadas) de propósito: o service apenas lança, e um tratador global (A8) transforma em resposta HTTP. Ninguém precisa de `try/catch`.

## A6. Service — regras e transação

`service/TurmaService.java`:

```java
package com.escola.buscaativa.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.escola.buscaativa.dto.TurmaRequest;
import com.escola.buscaativa.dto.TurmaResponse;
import com.escola.buscaativa.exception.RecursoNaoEncontradoException;
import com.escola.buscaativa.exception.RegraNegocioException;
import com.escola.buscaativa.model.Turma;
import com.escola.buscaativa.repository.TurmaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // padrão da classe: só leitura
public class TurmaService {

    private final TurmaRepository repository;

    public List<TurmaResponse> listar() {
        return repository.findAllByOrderByAnoLetivoDescNomeAsc()
                .stream()
                .map(TurmaResponse::from)          // method reference = t -> TurmaResponse.from(t)
                .toList();
    }

    public TurmaResponse buscarPorId(Long id) {
        return TurmaResponse.from(buscarEntidade(id));
    }

    @Transactional                                  // sobrescreve: este método escreve
    public TurmaResponse criar(TurmaRequest dados) {
        validarNomeUnico(dados, null);
        Turma turma = new Turma();
        copiarDados(dados, turma);
        return TurmaResponse.from(repository.save(turma));
    }

    @Transactional
    public TurmaResponse atualizar(Long id, TurmaRequest dados) {
        Turma turma = buscarEntidade(id);
        validarNomeUnico(dados, id);
        copiarDados(dados, turma);
        return TurmaResponse.from(turma);           // sem save()! veja "dirty checking" abaixo
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarEntidade(id));
    }

    // ---------- auxiliares ----------

    private Turma buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma " + id + " não encontrada"));
    }

    private void validarNomeUnico(TurmaRequest dados, Long idAtual) {
        repository.findByNomeIgnoreCaseAndAnoLetivo(dados.nome().trim(), dados.anoLetivo())
                .filter(existente -> !existente.getId().equals(idAtual))   // ignora a própria turma na edição
                .ifPresent(existente -> {
                    throw new RegraNegocioException(
                            "Já existe a turma " + existente.getNome() + " em " + existente.getAnoLetivo());
                });
    }

    private void copiarDados(TurmaRequest dados, Turma turma) {
        turma.setNome(dados.nome().trim());
        turma.setTurno(dados.turno());
        turma.setAnoLetivo(dados.anoLetivo());
    }
}
```

Os conceitos importantes:

**`@Transactional`**: é o seu `begin()` / `commit()` / `rollback()` do `AbstractFacade`, feito pelo Spring:
- O método termina normalmente → **commit**.
- Lança `RuntimeException` → **rollback** automático. (Aquele try/catch com rollback que o JSF precisava sumiu.)
- `readOnly = true` na classe → leituras mais leves. Os métodos de escrita sobrescrevem com `@Transactional`.

**Dirty checking (por que `atualizar` não chama `save`)**: a entidade veio do `findById` *dentro* da transação, então ela está **gerenciada** pelo Hibernate. Qualquer `setX()` é detectado e vira `UPDATE` no commit. É o mesmo comportamento do `EntityManager` que você já conhecia, só que agora a transação é declarativa.

**Fluxo funcional com `Optional`/`Stream`**: `orElseThrow`, `filter`, `ifPresent`, `map`… Vale se acostumar, pois aparecem o tempo todo em código Spring.

## A7. Controller REST

`controller/TurmaController.java`:

```java
package com.escola.buscaativa.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.escola.buscaativa.dto.TurmaRequest;
import com.escola.buscaativa.dto.TurmaResponse;
import com.escola.buscaativa.service.TurmaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService service;

    @GetMapping                                            // GET /api/turmas
    public List<TurmaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")                                   // GET /api/turmas/5
    public TurmaResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping                                           // POST /api/turmas
    public ResponseEntity<TurmaResponse> criar(@Valid @RequestBody TurmaRequest dados) {
        TurmaResponse criada = service.criar(dados);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criada.id())
                .toUri();
        return ResponseEntity.created(local).body(criada); // 201 Created + header Location
    }

    @PutMapping("/{id}")                                   // PUT /api/turmas/5
    public TurmaResponse atualizar(@PathVariable Long id, @Valid @RequestBody TurmaRequest dados) {
        return service.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")                                // DELETE /api/turmas/5
    @ResponseStatus(HttpStatus.NO_CONTENT)                 // 204: deu certo, sem corpo
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
```

| Anotação         | Significado                                                        |
|------------------|--------------------------------------------------------------------|
| `@PathVariable`  | pega o `{id}` da URL                                               |
| `@RequestBody`   | converte o JSON do corpo em `TurmaRequest` (Jackson)               |
| `@Valid`         | roda o Bean Validation no DTO **antes** de entrar no método        |
| `ResponseEntity` | controle total da resposta (status, headers, corpo)                |
| `@ResponseStatus`| define o status fixo quando o método retorna normalmente           |

**Convenção REST** (mesma URL, verbos diferentes):

| Ação       | Verbo    | URL                 | Sucesso             |
|------------|----------|---------------------|---------------------|
| Listar     | `GET`    | `/api/turmas`       | 200 + lista         |
| Buscar     | `GET`    | `/api/turmas/{id}`  | 200 + objeto        |
| Criar      | `POST`   | `/api/turmas`       | 201 + objeto criado |
| Atualizar  | `PUT`    | `/api/turmas/{id}`  | 200 + objeto        |
| Excluir    | `DELETE` | `/api/turmas/{id}`  | 204 sem corpo       |

Repare que o controller é **fino**: não tem regra, só traduz HTTP ↔ service.

## A8. Tratamento global de erros

Sem isso, uma `RecursoNaoEncontradoException` vira um **500** genérico. Vamos transformar cada exceção no status certo, com uma mensagem que o Angular consiga mostrar.

`exception/GlobalExceptionHandler.java`:

```java
package com.escola.buscaativa.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice      // "interceptador" de exceções de todos os controllers
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());       // 404
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail regraNegocio(RegraNegocioException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());        // 409
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)                                 // falha do @Valid
    public ProblemDetail validacao(MethodArgumentNotValidException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos"); // 400
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.put(erro.getField(), erro.getDefaultMessage()));
        problema.setProperty("campos", campos);
        return problema;
    }
}
```

`ProblemDetail` é o formato padrão de erro HTTP (RFC 9457). A resposta fica assim:

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "Dados inválidos",
  "campos": { "nome": "Informe o nome da turma" }
}
```

> Comparação: é o papel do `FacesMessage` + `<p:messages>`, só que como **dado**. O front decide como exibir.

## A9. Testar a API sem front

Crie `backend/http/turmas.http`. O IntelliJ executa esse arquivo: aparece um ▶ ao lado de cada requisição. No VS Code, use a extensão *REST Client*.

```http
### Listar
GET http://localhost:8080/api/turmas

### Criar
POST http://localhost:8080/api/turmas
Content-Type: application/json

{ "nome": "1º Ano A", "turno": "MANHA", "anoLetivo": 2026 }

### Criar inválida (espera 400)
POST http://localhost:8080/api/turmas
Content-Type: application/json

{ "nome": "", "turno": null, "anoLetivo": 1500 }

### Criar duplicada (espera 409)
POST http://localhost:8080/api/turmas
Content-Type: application/json

{ "nome": "1º ano a", "turno": "TARDE", "anoLetivo": 2026 }

### Buscar
GET http://localhost:8080/api/turmas/1

### Atualizar
PUT http://localhost:8080/api/turmas/1
Content-Type: application/json

{ "nome": "1º Ano A", "turno": "TARDE", "anoLetivo": 2026 }

### Buscar inexistente (espera 404)
GET http://localhost:8080/api/turmas/9999

### Excluir (espera 204)
DELETE http://localhost:8080/api/turmas/1
```

✅ **Checkpoint:** todos os status batem com o esperado, e no log aparecem os SQLs (`insert`, `update`, `delete`).

```bash
git add backend
git commit -m "feat(backend): CRUD de turma"
```

---

# PARTE B — Frontend

## B0. Mapa JSF → Angular para esta parte

| JSF                                         | Angular                                                    |
|---------------------------------------------|------------------------------------------------------------|
| `<h:dataTable>` / `<ui:repeat>`             | `@for (item of lista(); track item.id) { ... }`            |
| `rendered="#{...}"`                         | `@if (condicao) { ... } @else { ... }`                     |
| `#{bean.valor}`                             | `{{ valor() }}` (signal) ou `{{ valor }}`                  |
| `<h:inputText value="#{bean.nome}"/>`       | `<input formControlName="nome">` (Reactive Forms)          |
| `required="true"` / `f:validate...`         | `Validators.required`, `Validators.maxLength(50)`          |
| `action="#{bean.salvar}"`                   | `(ngSubmit)="salvar()"` / `(click)="excluir(t)"`           |
| `<h:link outcome="...">` / navegação        | `routerLink="/turmas"` / `router.navigate([...])`          |
| `<f:viewParam name="id">`                   | `ActivatedRoute` → `paramMap.get('id')`                    |
| `FacesMessage`                              | um `signal` de erro exibido com `@if`                      |

**Regra de ouro do Angular moderno:** todo estado que aparece na tela fica num `signal`. Quando você chama `.set()` ou `.update()`, o Angular sabe exatamente o que redesenhar.

## B1. Modelo (tipos TypeScript)

Crie `frontend/src/app/turmas/turma.model.ts`:

```ts
export type Turno = 'MANHA' | 'TARDE' | 'NOITE' | 'INTEGRAL';   // union type: só aceita esses valores

export const TURNOS: { valor: Turno; rotulo: string }[] = [
  { valor: 'MANHA', rotulo: 'Manhã' },
  { valor: 'TARDE', rotulo: 'Tarde' },
  { valor: 'NOITE', rotulo: 'Noite' },
  { valor: 'INTEGRAL', rotulo: 'Integral' },
];

export interface Turma {          // "formato" do JSON que vem do TurmaResponse
  id: number;
  nome: string;
  turno: Turno;
  anoLetivo: number;
}

export type TurmaRequest = Omit<Turma, 'id'>;   // tudo de Turma menos o id (= TurmaRequest do Java)
```

Em TypeScript, `interface` **não existe em tempo de execução**. Ela serve só para o compilador checar os tipos. É um "contrato" com o backend.

## B2. Service (fala com a API)

Crie `frontend/src/app/turmas/turma.service.ts`:

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Turma, TurmaRequest } from './turma.model';

@Injectable({ providedIn: 'root' })   // singleton na aplicação inteira (≈ @ApplicationScoped)
export class TurmaService {

  private http = inject(HttpClient);
  private readonly url = '/api/turmas';

  listar(): Observable<Turma[]> {
    return this.http.get<Turma[]>(this.url);
  }

  buscar(id: number): Observable<Turma> {
    return this.http.get<Turma>(`${this.url}/${id}`);   // template string: crase + ${}
  }

  criar(dados: TurmaRequest): Observable<Turma> {
    return this.http.post<Turma>(this.url, dados);
  }

  atualizar(id: number, dados: TurmaRequest): Observable<Turma> {
    return this.http.put<Turma>(`${this.url}/${id}`, dados);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
```

Note: os métodos **não executam** a requisição. Eles devolvem um `Observable`, e a requisição só sai quando alguém chama `.subscribe()`. É uma "receita" que só é preparada quando alguém pede.

## B3. Gerar os componentes

Em `frontend/`:

```bash
ng generate component turmas/turma-lista
```

```bash
ng generate component turmas/turma-form
```

Cada comando cria 4 arquivos (`.ts`, `.html`, `.css`, `.spec.ts`), e as classes se chamam `TurmaLista` e `TurmaForm`.

## B4. Tela de listagem

`turmas/turma-lista/turma-lista.ts`:

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Turma, TURNOS, Turno } from '../turma.model';
import { TurmaService } from '../turma.service';

@Component({
  selector: 'app-turma-lista',
  imports: [RouterLink],                  // tudo que o template usa precisa ser importado aqui
  templateUrl: './turma-lista.html',
  styleUrl: './turma-lista.css',
})
export class TurmaLista implements OnInit {

  private service = inject(TurmaService);

  turmas = signal<Turma[]>([]);
  carregando = signal(true);
  erro = signal<string | null>(null);

  ngOnInit(): void {                      // ciclo de vida: roda uma vez ao abrir a tela (≈ @PostConstruct)
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.service.listar().subscribe({
      next: lista => {                    // sucesso
        this.turmas.set(lista);
        this.carregando.set(false);
      },
      error: () => {                      // falha (backend fora, 500…)
        this.erro.set('Não foi possível carregar as turmas.');
        this.carregando.set(false);
      },
    });
  }

  excluir(turma: Turma): void {
    if (!confirm(`Excluir a turma ${turma.nome}?`)) {
      return;
    }
    this.service.excluir(turma.id).subscribe({
      // remove da lista local sem recarregar tudo
      next: () => this.turmas.update(lista => lista.filter(t => t.id !== turma.id)),
      error: () => this.erro.set('Não foi possível excluir a turma.'),
    });
  }

  rotuloTurno(turno: Turno): string {
    return TURNOS.find(t => t.valor === turno)?.rotulo ?? turno;   // ?. e ?? = segurança contra undefined
  }
}
```

`turmas/turma-lista/turma-lista.html`:

```html
<div class="cabecalho">
  <h2>Turmas</h2>
  <a routerLink="/turmas/nova" class="botao">+ Nova turma</a>
</div>

@if (erro()) {
  <p class="erro">{{ erro() }}</p>
}

@if (carregando()) {
  <p>Carregando...</p>
} @else {
  <table>
    <thead>
      <tr>
        <th>Nome</th>
        <th>Turno</th>
        <th>Ano letivo</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      @for (turma of turmas(); track turma.id) {
        <tr>
          <td>{{ turma.nome }}</td>
          <td>{{ rotuloTurno(turma.turno) }}</td>
          <td>{{ turma.anoLetivo }}</td>
          <td class="acoes">
            <a [routerLink]="['/turmas', turma.id, 'editar']">Editar</a>
            <button type="button" (click)="excluir(turma)">Excluir</button>
          </td>
        </tr>
      } @empty {
        <tr>
          <td colspan="4">Nenhuma turma cadastrada.</td>
        </tr>
      }
    </tbody>
  </table>
}
```

Sintaxe de template (as 3 formas de *binding*):

| Sintaxe            | Direção          | Exemplo                                  |
|--------------------|------------------|------------------------------------------|
| `{{ expr }}`       | classe → tela    | `{{ turma.nome }}`                       |
| `[prop]="expr"`    | classe → atributo| `[routerLink]="['/turmas', turma.id]"`   |
| `(evento)="metodo()"` | tela → classe | `(click)="excluir(turma)"`               |

- `track turma.id` é obrigatório no `@for`: diz ao Angular como identificar cada linha, para redesenhar só o que mudou.
- `@empty` é o que aparece quando a lista está vazia (como o `emptyMessage` do `p:dataTable`).

## B5. Tela de formulário (criar e editar)

A mesma tela serve para as duas coisas: se a URL tem `:id`, é edição.

`turmas/turma-form/turma-form.ts`:

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TURNOS, Turno } from '../turma.model';
import { TurmaService } from '../turma.service';

@Component({
  selector: 'app-turma-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './turma-form.html',
  styleUrl: './turma-form.css',
})
export class TurmaForm implements OnInit {

  private fb = inject(NonNullableFormBuilder);
  private service = inject(TurmaService);
  private route = inject(ActivatedRoute);   // dados da rota atual (parâmetros da URL)
  private router = inject(Router);          // navegação via código

  readonly turnos = TURNOS;

  idEdicao = signal<number | null>(null);
  salvando = signal(false);
  erro = signal<string | null>(null);

  // o formulário: cada chave = um campo, com [valor inicial, validadores]
  form = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(50)]],
    turno: ['MANHA' as Turno, Validators.required],
    anoLetivo: [new Date().getFullYear(), [Validators.required, Validators.min(2000), Validators.max(2100)]],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');   // "5" em /turmas/5/editar, null em /turmas/nova
    if (id) {
      this.idEdicao.set(Number(id));
      this.service.buscar(Number(id)).subscribe({
        next: turma => this.form.patchValue(turma),       // preenche os campos com o que veio da API
        error: () => this.erro.set('Turma não encontrada.'),
      });
    }
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();       // faz as mensagens de erro aparecerem
      return;
    }

    this.salvando.set(true);
    this.erro.set(null);

    const dados = this.form.getRawValue();
    const id = this.idEdicao();
    const requisicao = id ? this.service.atualizar(id, dados) : this.service.criar(dados);

    requisicao.subscribe({
      next: () => this.router.navigate(['/turmas']),
      error: (e: HttpErrorResponse) => {
        this.erro.set(e.error?.detail ?? 'Erro ao salvar a turma.');   // "detail" do ProblemDetail (A8)
        this.salvando.set(false);
      },
    });
  }
}
```

`turmas/turma-form/turma-form.html`:

```html
<h2>{{ idEdicao() ? 'Editar turma' : 'Nova turma' }}</h2>

<form [formGroup]="form" (ngSubmit)="salvar()">

  <label>
    Nome
    <input formControlName="nome" placeholder="Ex.: 1º Ano A" />
  </label>
  @if (form.controls.nome.touched && form.controls.nome.invalid) {
    <small class="erro">Informe o nome (até 50 caracteres).</small>
  }

  <label>
    Turno
    <select formControlName="turno">
      @for (t of turnos; track t.valor) {
        <option [value]="t.valor">{{ t.rotulo }}</option>
      }
    </select>
  </label>

  <label>
    Ano letivo
    <input type="number" formControlName="anoLetivo" />
  </label>
  @if (form.controls.anoLetivo.touched && form.controls.anoLetivo.invalid) {
    <small class="erro">Ano letivo inválido.</small>
  }

  @if (erro()) {
    <p class="erro">{{ erro() }}</p>
  }

  <div class="acoes">
    <button type="submit" [disabled]="salvando()">
      {{ salvando() ? 'Salvando...' : 'Salvar' }}
    </button>
    <a routerLink="/turmas">Cancelar</a>
  </div>
</form>
```

**Reactive Forms em 4 linhas:**
- O formulário é definido **na classe** (`fb.group`), não no HTML. O HTML só se liga a ele com `[formGroup]` e `formControlName`.
- `form.invalid`, `form.controls.nome.touched`, etc. dão o estado de cada campo.
- `getRawValue()` devolve o objeto `{ nome, turno, anoLetivo }`, já no formato do `TurmaRequest`.
- `NonNullableFormBuilder`: ao "resetar", os campos voltam ao valor inicial em vez de `null`, o que simplifica a tipagem.

> **Validação em dobro?** Sim, e é proposital. A do front serve para a **experiência do usuário** (feedback imediato). A do back serve para a **segurança** (qualquer um pode chamar a API direto, sem passar pela tela). A regra "nome duplicado" só existe no back, e o erro 409 chega na tela pelo `e.error.detail`.

## B6. Rotas

`frontend/src/app/app.routes.ts`:

```ts
import { Routes } from '@angular/router';

import { TurmaForm } from './turmas/turma-form/turma-form';
import { TurmaLista } from './turmas/turma-lista/turma-lista';

export const routes: Routes = [
  { path: '', redirectTo: 'turmas', pathMatch: 'full' },
  { path: 'turmas', component: TurmaLista },
  { path: 'turmas/nova', component: TurmaForm },
  { path: 'turmas/:id/editar', component: TurmaForm },   // :id = parâmetro
];
```

O Angular troca o componente dentro do `<router-outlet />` **sem recarregar a página** (SPA, *Single Page Application*). Repare na barra de endereço: a URL muda, mas a página nunca pisca.

## B7. Layout (componente raiz)

Agora o `App` vira só a "moldura" (cabeçalho + área onde as telas aparecem). O teste do `/api/ping` pode sair daqui.

`frontend/src/app/app.ts`:

```ts
import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
```

`frontend/src/app/app.html`:

```html
<header class="topo">
  <strong>Busca Ativa</strong>
  <nav>
    <a routerLink="/turmas" routerLinkActive="ativo">Turmas</a>
  </nav>
</header>

<main class="conteudo">
  <router-outlet />
</main>
```

Esse é o seu `template.xhtml` com `<ui:insert>`: o `router-outlet` é o ponto de inserção.

## B8. Um mínimo de CSS

`frontend/src/styles.css` (global):

```css
* { box-sizing: border-box; }
body { margin: 0; font-family: system-ui, sans-serif; background: #f5f6f8; color: #222; }

.topo { display: flex; gap: 24px; align-items: center; padding: 12px 24px; background: #1f4e79; color: #fff; }
.topo a { color: #fff; text-decoration: none; opacity: .8; }
.topo a.ativo { opacity: 1; font-weight: 600; }

.conteudo { max-width: 900px; margin: 24px auto; padding: 0 16px; }

table { width: 100%; border-collapse: collapse; background: #fff; }
th, td { padding: 10px; border-bottom: 1px solid #e3e3e3; text-align: left; }

form { display: flex; flex-direction: column; gap: 12px; max-width: 420px; }
label { display: flex; flex-direction: column; gap: 4px; }
input, select { padding: 8px; border: 1px solid #ccc; border-radius: 4px; }

button, .botao { padding: 8px 14px; border: 0; border-radius: 4px; background: #1f4e79; color: #fff; cursor: pointer; text-decoration: none; }
button:disabled { opacity: .6; }

.cabecalho { display: flex; justify-content: space-between; align-items: center; }
.acoes { display: flex; gap: 12px; align-items: center; }
.erro { color: #b00020; }
```

> Mais para frente dá para trocar esse CSS na mão pelo **PrimeNG**, que é o "PrimeFaces do Angular", com os mesmos nomes de componentes (`p-table`, `p-button`…). Primeiro vale entender o Angular puro.

## B9. Testar tudo

1. Backend rodando (`.\mvnw spring-boot:run`).
2. `ng serve` no `frontend/`.
3. Abra http://localhost:4200, que redireciona para `/turmas`.

Roteiro de teste:
- [ ] Lista vazia mostra "Nenhuma turma cadastrada."
- [ ] Criar uma turma → volta para a lista com ela.
- [ ] Salvar com nome vazio → mensagem vermelha, e nada é enviado (veja a aba *Network* do F12).
- [ ] Criar uma turma duplicada → aparece "Já existe a turma…" (veio do backend, status 409).
- [ ] Editar → campos vêm preenchidos → salvar → lista atualizada.
- [ ] Excluir → confirmação → some da lista.
- [ ] Pare o backend e recarregue → "Não foi possível carregar as turmas."

✅ **Checkpoint:** todos os itens acima funcionando.

```bash
git add .
git commit -m "feat(frontend): CRUD de turma"
git push
```

---

## Resumo

| Backend                                          | Frontend                                                |
|--------------------------------------------------|---------------------------------------------------------|
| `@Entity` + Lombok → tabela criada pelo Hibernate | `interface Turma` = contrato com o JSON                 |
| `JpaRepository` + query derivada do nome         | `TurmaService` com `HttpClient` → `Observable`          |
| `record` DTO + Bean Validation                   | Componentes com `signal` + `@if` / `@for`               |
| `@Service` + `@Transactional` + dirty checking   | Reactive Forms + `Validators`                           |
| `@RestController` + verbos HTTP + status         | Rotas + `routerLink` + `router.navigate`                |
| `@RestControllerAdvice` + `ProblemDetail`        | `error: (e) => e.error.detail` exibido na tela          |

## Exercícios para fixar (faça sozinho antes do Guia 03)

1. Adicione o campo `boolean ativa` em `Turma` (padrão `true`), passando por todas as camadas: entity → DTOs → service → model TS → form (checkbox) → lista.
2. No backend, crie `GET /api/turmas?anoLetivo=2026` usando `@RequestParam(required = false)` e um novo método derivado no repository.
3. Na lista, adicione um `<select>` de ano letivo que chama o endpoint do exercício 2.

## Próximo: Guia 03 — Aluno e Responsável

Primeiro relacionamento: `@ManyToOne` (Aluno → Turma) e `@OneToMany`, DTOs com dados aninhados e um `<select>` de turmas no formulário de aluno.
