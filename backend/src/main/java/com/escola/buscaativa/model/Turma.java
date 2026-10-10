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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Turno turno;

    @Column(nullable = false)
    private Integer anoLetivo;

}
