package com.escola.buscaativa.repository;

import com.escola.buscaativa.model.Turma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurmaRepository extends JpaRepository<Turma, Long> {

    List<Turma> findAllByOrderByAnoLetivoDescNomeAsc();

    Optional<Turma> findByNomeIgnoreCaseAndAnoLetivo(String nome, Integer anoLetivo);

}
