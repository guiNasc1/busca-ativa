# Guia 01 — Setup do projeto (Git + Spring Boot + Angular)

> Objetivo: terminar este guia com **backend e frontend rodando e conversando entre si**, tudo versionado no Git.
> Cada etapa termina com um ✅ **checkpoint** e um **commit**.

---

## 0. O mapa mental: JSF → Spring Boot + Angular

No JSF, **uma aplicação só** fazia tudo: o servidor montava o HTML (xhtml) e guardava estado da tela no managed bean.
Agora são **duas aplicações separadas** que conversam por HTTP + JSON:

```
 Navegador                                  Servidor
┌──────────────────────┐   HTTP/JSON   ┌──────────────────────────┐   JDBC   ┌──────────┐
│ Angular (porta 4200) │ ────────────► │ Spring Boot (porta 8080) │ ───────► │ Supabase │
│ telas, rotas, estado │ ◄──────────── │ regras, API REST, JPA    │          │ Postgres │
└──────────────────────┘               └──────────────────────────┘          └──────────┘
```

| No JSF você tinha…                          | Agora é…                                                     |
|---------------------------------------------|--------------------------------------------------------------|
| Tomcat externo + deploy de WAR              | Tomcat **embutido** no JAR — roda com um `main()`            |
| `pom.xml` montado na mão (Weld, Mojarra…)   | **Starters** do Spring Boot (um starter = pacote coerente)   |
| `persistence.xml`                           | `application.properties`                                     |
| `EntityManagerProducer` + `AbstractFacade`  | `JpaRepository` (Spring Data gera o CRUD sozinho)            |
| CDI: `@Inject`, `@RequestScoped`…            | Spring: injeção pelo construtor, `@Service`, `@Component`…   |
| Managed bean ligado ao xhtml                 | `@RestController` devolvendo JSON                            |
| `.xhtml` + PrimeFaces                        | Componentes Angular (`.ts` + `.html`) — PrimeNG existe!      |
| `#{bean.prop}` (EL)                          | `{{ prop }}` / `[prop]` / `(evento)` no template Angular     |
| Filtro de login + `@SessionScoped`          | Spring Security (etapa futura)                               |

---

## 1. Pré-requisitos (já conferidos na sua máquina ✔)

| Ferramenta  | Versão encontrada |
|-------------|-------------------|
| JDK         | 21 (Zulu)         |
| Maven       | 3.9.15            |
| Node / npm  | 24 / 11           |
| Angular CLI | 22                |
| Git         | 2.54              |

IDEs sugeridas: **IntelliJ** para o `backend/`, **VS Code** (extensão *Angular Language Service*) para o `frontend/`.

---

## 2. Estrutura do repositório (monorepo)

Um único repositório Git com as duas aplicações:

```
buscaativa/            ← raiz do git
├── .gitignore
├── README.md
├── docs/              ← estes guias
├── backend/           ← Spring Boot
└── frontend/          ← Angular
```

### 2.1 `.gitignore` da raiz

Crie `buscaativa/.gitignore`:

```gitignore
# ---------- Backend ----------
backend/target/
# arquivo com as credenciais do banco (equivale ao seu persistence.xml ignorado)
backend/src/main/resources/application-dev.properties

# ---------- Frontend ----------
frontend/node_modules/
frontend/dist/
frontend/.angular/

# ---------- IDEs / SO ----------
.idea/
*.iml
.vscode/
Thumbs.db
.DS_Store
```

### 2.2 `README.md`

Algo curto, ex.:

```markdown
# Busca Ativa
Sistema de busca ativa escolar. Backend Spring Boot (`backend/`) + frontend Angular (`frontend/`).
```

### 2.3 Primeiro commit

> O Git não versiona pasta vazia — por isso `backend/` e `frontend/` ainda não aparecem.

```bash
git add .gitignore README.md docs/
git commit -m "chore: estrutura inicial do monorepo"
```

### 2.4 Repositório no GitHub

O repo `guiNasc1/busca-ativa` é da versão JSF. Recomendo **criar um novo** (ex.: `busca-ativa-spring`, privado) e deixar o antigo arquivado como referência.

Pelo site: *New repository* → **sem** README/.gitignore (você já tem). Depois:

```bash
git remote add origin https://github.com/guiNasc1/busca-ativa-spring.git
git push -u origin main
```

✅ **Checkpoint:** o repositório aparece no GitHub com o README.

---

## 3. Backend — Spring Boot

### 3.1 Gerar o projeto (Spring Initializr)

O [start.spring.io](https://start.spring.io) é o "gerador oficial". Você pode usar o site ou este comando (rodar **na raiz** `buscaativa/`).

> Se a pasta `backend/` existir vazia, apague antes — o zip cria ela de novo.

```bash
curl.exe -G https://start.spring.io/starter.zip -d type=maven-project -d language=java -d javaVersion=21 -d groupId=com.escola -d artifactId=buscaativa -d name=buscaativa -d packageName=com.escola.buscaativa -d packaging=jar -d dependencies=web,data-jpa,postgresql,validation,devtools,lombok -d baseDir=backend -o backend.zip
```

```bash
Expand-Archive backend.zip -DestinationPath . -Force; Remove-Item backend.zip
```

**Se for pelo site**, marque: Maven · Java · versão estável padrão do Boot · Group `com.escola` · Artifact `buscaativa` · Packaging **Jar** · Java **21**, e as dependências abaixo.

**O que cada dependência faz:**

| Dependência         | Para quê                                               | Equivalente no JSF              |
|---------------------|--------------------------------------------------------|---------------------------------|
| Spring Web          | API REST + Tomcat embutido                             | Tomcat + Servlet + JAX-RS       |
| Spring Data JPA     | Hibernate + repositórios prontos                       | Hibernate + seu AbstractFacade  |
| PostgreSQL Driver   | Driver JDBC                                            | o mesmo driver                  |
| Validation          | `@NotBlank`, `@Size`… (Bean Validation)                | o mesmo Bean Validation         |
| DevTools            | Reinicia a app ao salvar código                        | (não tinha)                     |
| Lombok              | Gera getters, setters, construtores… na compilação     | o mesmo Lombok                  |

> No Spring Boot 4 o starter "Spring Web" aparece no `pom.xml` como `spring-boot-starter-webmvc` (no Boot 3 era `spring-boot-starter-web`). Mesma coisa.
>
> Lombok é explicado na seção **3.3**. Para DTOs (objetos que trafegam na API) vamos usar `record` do Java, que já é imutável e enxuto sem precisar de Lombok.

### 3.2 Anatomia do que foi gerado

```
backend/
├── mvnw, mvnw.cmd          ← Maven Wrapper: garante a mesma versão do Maven em qualquer PC
├── pom.xml
└── src/main/
    ├── java/com/escola/buscaativa/
    │   └── BuscaativaApplication.java
    └── resources/
        └── application.properties
```

Abra o `pom.xml` e repare:

- `<parent>spring-boot-starter-parent</parent>` → ele define as **versões compatíveis** de tudo. Por isso as dependências **não têm `<version>`** — acabou o sofrimento de alinhar Hibernate/Weld/Mojarra na mão.
- `spring-boot-maven-plugin` → empacota um **JAR executável** (com Tomcat dentro).
- `maven-compiler-plugin` com `<annotationProcessorPaths>` contendo o `lombok` → é o que faz o Lombok rodar na compilação (veja 3.3).

Abra `BuscaativaApplication.java`:

```java
@SpringBootApplication
public class BuscaativaApplication {
    public static void main(String[] args) {
        SpringApplication.run(BuscaativaApplication.class, args);
    }
}
```

`@SpringBootApplication` liga três coisas:
1. **Configuração automática** (auto-configuration): viu o driver do Postgres e o JPA no classpath? Ele cria `DataSource`, `EntityManagerFactory` e gerenciador de transações sozinho. **É o seu `EntityManagerProducer` que deixa de existir.**
2. **Component scan** a partir deste pacote: toda classe com `@Component`, `@Service`, `@Repository`, `@RestController` dentro de `com.escola.buscaativa.*` vira um bean (como o `beans.xml` + CDI).
3. **Escaneia `@Entity`** automaticamente — nada de listar `<class>` como no Tomcat puro.

> ⚠️ Por causa do item 2, **todas as suas classes precisam ficar dentro de `com.escola.buscaativa`** (ou subpacotes).

### 3.3 Lombok — menos código repetitivo

**O que é:** um *annotation processor*. Durante a **compilação**, ele lê as anotações e escreve no `.class` o código que você não quer digitar (getters, setters, construtores…). Em tempo de execução o Lombok nem existe — por isso no `pom.xml` ele aparece como `<optional>true</optional>` e é registrado no `maven-compiler-plugin` (`annotationProcessorPaths`). O Initializr já faz isso por você.

**IntelliJ:** o plugin do Lombok já vem embutido. Se a IDE mostrar "getX() não existe" em vermelho, ative em *Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing*.

#### Anotações que vamos usar

| Anotação                      | Gera                                                                     | Onde usar no projeto                    |
|-------------------------------|--------------------------------------------------------------------------|-----------------------------------------|
| `@Getter` / `@Setter`         | getters/setters de todos os campos (ou só do campo anotado)              | Entidades                               |
| `@NoArgsConstructor`          | construtor sem argumentos                                                | Entidades (o JPA **exige** um)          |
| `@AllArgsConstructor`         | construtor com todos os campos                                           | Junto com `@Builder`                    |
| `@RequiredArgsConstructor`    | construtor com os campos `final` (e `@NonNull`)                          | **Services e Controllers** (injeção)    |
| `@Builder`                    | API fluente: `Turma.builder().nome("1A").build()`                        | Testes, criação de objetos complexos    |
| `@ToString`                   | `toString()` (use `@ToString.Exclude` em relacionamentos)                | Debug                                   |
| `@EqualsAndHashCode`          | `equals()`/`hashCode()` (`onlyExplicitlyIncluded = true` + `.Include`)   | Entidades, com cuidado                  |
| `@Slf4j`                      | campo `log` pronto: `log.info("...")`                                    | Qualquer classe que precise logar       |
| `@Data`                       | getter + setter + toString + equals/hashCode + required constructor      | Classes simples **que não são @Entity** |

#### Exemplo 1 — Entidade (prévia do Guia 02)

```java
@Entity
@Getter
@Setter
@NoArgsConstructor                 // JPA precisa instanciar a entidade "vazia"
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
}
```

Sem Lombok, seriam mais 4 métodos (get/set de `id` e `nome`) + o construtor — e cresce a cada campo novo.

#### Exemplo 2 — Injeção de dependência pelo construtor

No CDI você fazia `@Inject private TurmaFacade facade;`. No Spring, a forma recomendada é **injeção pelo construtor** com campo `final` (fica imutável e fácil de testar). O Lombok escreve o construtor para você:

```java
@Service
@RequiredArgsConstructor           // gera: public TurmaService(TurmaRepository repository) { this.repository = repository; }
@Slf4j                             // gera: private static final Logger log = LoggerFactory.getLogger(TurmaService.class);
public class TurmaService {

    private final TurmaRepository repository;

    public Turma salvar(Turma turma) {
        log.info("Salvando turma {}", turma.getNome());
        return repository.save(turma);
    }
}
```

Quando a classe tem **um único construtor**, o Spring o usa para injetar automaticamente — nem precisa de `@Autowired`.

#### Exemplo 3 — `@Builder`

```java
Turma t = Turma.builder()
        .nome("1º Ano A")
        .build();
```

> Em entidade com `@Builder`, adicione também `@NoArgsConstructor` **e** `@AllArgsConstructor` (o builder precisa de um construtor com todos os campos, e o JPA precisa do vazio).

#### ⚠️ Armadilha: não use `@Data` em `@Entity`

`@Data` gera `equals`, `hashCode` e `toString` usando **todos os campos**. Em entidades com relacionamentos (`Turma` ↔ `Aluno`), isso causa:
- **`StackOverflowError`**: `Turma.toString()` chama `Aluno.toString()`, que chama `Turma.toString()`… infinito;
- **`LazyInitializationException`** ou consultas extras ao acessar coleções *lazy*;
- `hashCode` que muda depois do `save` (o `id` passa de `null` para um número) — quebra `HashSet`.

**Regra do projeto:** entidade = `@Getter @Setter @NoArgsConstructor` (+ `@ToString.Exclude` nos relacionamentos, se usar `@ToString`). `@Data` só em classes simples que não são entidades.

### 3.4 Configurar o banco (Supabase)

A ideia: o arquivo **commitado** não tem senha; as credenciais ficam num arquivo de **profile** ignorado pelo Git.

**`backend/src/main/resources/application.properties`** (vai para o Git):

```properties
spring.application.name=buscaativa

# ativa o profile "dev" -> o Spring também carrega application-dev.properties
spring.profiles.active=dev

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
# não manter a sessão do Hibernate aberta durante a renderização (boa prática em APIs REST)
spring.jpa.open-in-view=false
```

**`backend/src/main/resources/application-dev.properties`** (NÃO vai para o Git — está no `.gitignore` da raiz):

```properties
spring.datasource.url=jdbc:postgresql://SEU_HOST_SUPABASE:5432/postgres?sslmode=require
spring.datasource.username=SEU_USUARIO
spring.datasource.password=SUA_SENHA
```

Pegue os valores em **Supabase → Project → Connect**. Use a string do **Session pooler** (funciona em redes só IPv4). O usuário do pooler tem o formato `postgres.<id-do-projeto>`.

> Comparação: é exatamente o que você fazia com o `persistence.xml` fora do Git, só que agora o arquivo sem segredo **pode** ser versionado.

### 3.5 Primeiro endpoint REST ("hello world")

Crie `backend/src/main/java/com/escola/buscaativa/controller/PingController.java`:

```java
package com.escola.buscaativa.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j                       // Lombok: cria o campo "log"
@RestController              // bean que responde HTTP; o retorno vira JSON automaticamente
@RequestMapping("/api")      // prefixo de todas as URLs desta classe
public class PingController {

    @GetMapping("/ping")     // GET /api/ping
    public Map<String, String> ping() {
        log.info("Ping recebido");           // aparece no console do backend
        return Map.of("status", "ok");       // Jackson converte para {"status":"ok"}
    }
}
```

O que mudou em relação ao JSF: o controller **não sabe nada de tela**. Ele só recebe uma requisição e devolve dados. Quem desenha a tela é o Angular.

### 3.6 Rodar

```bash
cd backend
```

```bash
.\mvnw spring-boot:run
```

(Ou no IntelliJ: abra a pasta `backend/` e rode a classe `BuscaativaApplication`.)

No log, procure:
- `HikariPool-1 - Start completed` → **conectou no Supabase** (Hikari é o pool de conexões que o Boot configura sozinho).
- `Tomcat started on port 8080`.

Abra http://localhost:8080/api/ping → deve aparecer `{"status":"ok"}`, e no console do backend a linha `Ping recebido` (prova de que o `@Slf4j` funcionou).

✅ **Checkpoint:** JSON no navegador + log `Ping recebido` + sem erro de conexão.

```bash
git add backend
git commit -m "feat(backend): projeto Spring Boot com endpoint /api/ping"
```

> Antes do commit, rode `git status` e confirme que **`application-dev.properties` NÃO aparece** na lista.

---

## 4. Frontend — Angular

### 4.1 Gerar o projeto

Na **raiz** `buscaativa/` (apague antes a pasta `frontend/` se estiver vazia):

```bash
ng new buscaativa-web --directory frontend --skip-git --style=css --ssr=false
```

- `--directory frontend` → cria dentro de `frontend/`.
- `--skip-git` → **importante**: não criar um segundo repositório Git lá dentro (o repo é a raiz).
- `--ssr=false` → sem renderização no servidor; não precisamos disso.
- Se perguntar sobre ferramentas de IA / outras opções, pode aceitar o padrão.

### 4.2 Anatomia do que foi gerado

```
frontend/
├── angular.json            ← configuração de build/serve (tipo o pom.xml do front)
├── package.json            ← dependências npm (tipo <dependencies>)
├── node_modules/           ← libs baixadas (tipo ~/.m2) — nunca vai pro Git
└── src/
    ├── index.html          ← única página HTML real; contém <app-root>
    ├── main.ts             ← ponto de entrada (tipo o main() do Spring)
    ├── styles.css          ← CSS global
    └── app/
        ├── app.ts          ← componente raiz: a CLASSE (lógica + estado)
        ├── app.html        ← componente raiz: o TEMPLATE (a "view")
        ├── app.css         ← estilo só deste componente
        ├── app.config.ts   ← providers globais (tipo configuração de beans)
        └── app.routes.ts   ← rotas: URL → componente (tipo navegação do faces-config)
```

> Os nomes podem variar um pouco entre versões do Angular (versões antigas usavam `app.component.ts`). A ideia é a mesma.

**Conceito-chave — componente:** é a união de uma **classe TypeScript** (estado + métodos) com um **template HTML**. É o par *managed bean + xhtml* do JSF, só que rodando **no navegador**.

### 4.3 Rodar

```bash
cd frontend
```

```bash
ng serve
```

Abra http://localhost:4200 → página padrão do Angular.

✅ **Checkpoint:** página padrão abrindo.

```bash
git add frontend
git commit -m "feat(frontend): projeto Angular inicial"
```

---

## 5. Fazendo os dois conversarem

### 5.1 Proxy (evitar problema de CORS no desenvolvimento)

O navegador bloqueia por padrão chamadas de `localhost:4200` para `localhost:8080` (origens diferentes = **CORS**). Em desenvolvimento, o jeito mais simples é o servidor do Angular **repassar** as chamadas `/api` para o Spring.

Crie `frontend/proxy.conf.json`:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

No `frontend/angular.json`, procure o bloco `"serve"` e adicione `proxyConfig` dentro de `"options"` (crie o `"options"` se não existir):

```json
"serve": {
  "builder": "...(deixe o que já está)...",
  "options": {
    "proxyConfig": "proxy.conf.json"
  },
  ...
}
```

### 5.2 Habilitar o HttpClient

Em `frontend/src/app/app.config.ts`, adicione `provideHttpClient()` à lista de `providers` (se a sua versão já não trouxer):

```ts
import { provideHttpClient } from '@angular/common/http';

export const appConfig: ApplicationConfig = {
  providers: [
    // ...os que já existem...
    provideHttpClient()
  ]
};
```

`HttpClient` é o serviço do Angular para fazer requisições HTTP. "Prover" = registrar para poder ser injetado (como produzir um bean).

### 5.3 Chamar a API no componente raiz

Substitua o conteúdo de `frontend/src/app/app.ts` (mantenha os `imports` que já estavam no `@Component`, ex.: `RouterOutlet`):

```ts
import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',          // a tag <app-root> do index.html
  imports: [RouterOutlet],       // componentes/diretivas usados no template
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private http = inject(HttpClient);       // injeção de dependência (≈ @Inject)

  status = signal('carregando...');        // signal = valor reativo; ao mudar, a tela atualiza

  constructor() {
    this.http.get<{ status: string }>('/api/ping')   // GET assíncrono, tipado
      .subscribe(resposta => this.status.set(resposta.status));
  }
}
```

E troque **todo** o conteúdo de `frontend/src/app/app.html` por:

```html
<h1>Busca Ativa</h1>
<p>Backend respondeu: <strong>{{ status() }}</strong></p>

<router-outlet />
```

Pontos de sintaxe para fixar:
- `{{ status() }}` → interpolação, como `#{bean.status}`. Os `()` são porque **signal se lê chamando como função**.
- `http.get(...)` **não retorna o valor**, retorna um *Observable* (uma "promessa" de resposta). O `.subscribe(...)` diz o que fazer quando a resposta chegar. Diferente do JSF, aqui **tudo que vai ao servidor é assíncrono**.
- `inject(HttpClient)` → pede ao Angular a instância do serviço.

### 5.4 Testar ponta a ponta

1. Terminal 1: backend rodando (`.\mvnw spring-boot:run` em `backend/`).
2. Terminal 2: **reinicie** o `ng serve` em `frontend/` (o proxy só é lido na inicialização).
3. Abra http://localhost:4200 → deve aparecer **"Backend respondeu: ok"**.

Experimente: pare o backend e recarregue a página. O texto fica em "carregando..." e aparece erro no console (F12). Isso mostra que são **duas aplicações independentes**.

✅ **Checkpoint:** "Backend respondeu: ok".

```bash
git add .
git commit -m "feat: integração frontend-backend via proxy (/api/ping)"
git push
```

---

## 6. Resumo

| Etapa | Resultado |
|-------|-----------|
| 2 | Monorepo com `.gitignore` protegendo credenciais e `node_modules` |
| 3 | Spring Boot + Lombok rodando, conectado ao Supabase, com `GET /api/ping` |
| 4 | Angular rodando em `localhost:4200` |
| 5 | Angular chamando o Spring via proxy |

## Próximo: Guia 02 — CRUD de Turma

Vamos construir a primeira funcionalidade completa, camada por camada:

```
Backend:  Turma (@Entity + Lombok) → TurmaRepository (JpaRepository) → TurmaService (@RequiredArgsConstructor)
          → TurmaController (REST + DTO record)
Frontend: interface Turma → TurmaService (HttpClient) → componentes lista/formulário → rotas
```
