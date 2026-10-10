package com.escola.buscaativa.controller;

import com.escola.buscaativa.dto.TurmaRequest;
import com.escola.buscaativa.dto.TurmaResponse;
import com.escola.buscaativa.model.Turma;
import com.escola.buscaativa.service.TurmaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
