package com.escola.buscaativa.dto;

import com.escola.buscaativa.model.Turno;
import jakarta.validation.constraints.*;

public record TurmaRequest(

        @NotBlank(message = "Informe o nome da turma")
        @Size(max = 50, message = "O nome deve ter no máximo 50 caractéres")
        String nome,

        @NotNull(message = "Informe o turno")
        Turno turno,

        @NotNull(message = "Informe o ano letivo")
        @Min(value = 2000, message = "Ano letivo inválido")
        @Max(value = 2100, message = "Ano letivo inválido")
        Integer anoLetivo

) {}
