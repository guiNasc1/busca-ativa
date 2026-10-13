package com.escola.buscaativa.controller;

import com.escola.buscaativa.dto.TurmaRequest;
import com.escola.buscaativa.dto.TurmaResponse;
import com.escola.buscaativa.model.Turma;
import com.escola.buscaativa.service.TurmaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService service;

    @GetMapping
    public List<TurmaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public TurmaResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<TurmaResponse> criar(@Valid @RequestBody TurmaRequest dados){
        TurmaResponse criada = service.criar(dados);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criada.id())
                .toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @PutMapping("/{id}")
    public TurmaResponse atualizar(@PathVariable Long id, @Valid @RequestBody TurmaRequest dados){
        return service.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id){
        service.excluir(id);
    }
}
