package com.escola.buscaativa.dto;

import com.escola.buscaativa.model.Turma;
import com.escola.buscaativa.model.Turno;

public record TurmaResponse(Long id, String nome, Turno turno, Integer anoLetivo) {

    public static TurmaResponse from(Turma turma) {
        return new TurmaResponse(turma.getId(), turma.getNome(), turma.getTurno(), turma.getAnoLetivo());
    }
}
