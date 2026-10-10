package com.escola.buscaativa.service;

import com.escola.buscaativa.dto.TurmaRequest;
import com.escola.buscaativa.dto.TurmaResponse;
import com.escola.buscaativa.exception.RegraNegocioException;
import com.escola.buscaativa.model.Turma;
import com.escola.buscaativa.repository.TurmaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TurmaService {

    private final TurmaRepository repository;

    public List<TurmaResponse> listar() {
        return repository.findAllByOrderByAnoLetivoDescNomeAsc()
                .stream()
                .map(TurmaResponse::from)
                .toList();
    }

    public TurmaResponse buscarPorId(Long idTurma) {
        return TurmaResponse.from(repository.findById(idTurma).orElseThrow());
    }

    @Transactional
    public TurmaResponse criar(TurmaRequest dados) {
        validarNomeUnico(dados, null);
        Turma turma = new Turma();
        copiarDados(dados, turma);
        return TurmaResponse.from(turma);
    }

    @Transactional
    public TurmaResponse atualizar(Long id, TurmaRequest dados) {
        Turma turma = buscarEntidade(id);
        validarNomeUnico(dados, id);
        copiarDados(dados, turma);
        return TurmaResponse.from(turma);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarEntidade(id));
    }

    // --- métodos auxiliares ---

    public Turma buscarEntidade(Long idTurma) {
        return repository.findById(idTurma).orElseThrow(
                () -> new RegraNegocioException("Turma " + idTurma + " não encontrada")
        );
    }

    private void validarNomeUnico(TurmaRequest dados, Long idAtual) {
        repository.findByNomeIgnoreCaseAndAnoLetivo(dados.nome(), dados.anoLetivo())
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw new RegraNegocioException(
                            "Já existe a turma " + existente.getNome() + " em " +
                                    existente.getAnoLetivo()
                    );
                });
    }

    public void copiarDados(TurmaRequest dados, Turma turma) {
        turma.setNome(dados.nome().trim());
        turma.setAnoLetivo(dados.anoLetivo());
        turma.setTurno(dados.turno());
    }

}
