package pt.karate.utentes.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.utentes.domain.Aluno;
import pt.karate.utentes.domain.DadosFiscais;
import pt.karate.utentes.domain.Parentesco;
import pt.karate.utentes.domain.Pessoa;
import pt.karate.utentes.domain.RelacaoResponsavel;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.PessoaSnapshot;
import pt.karate.utentes.events.Topicos;
import pt.karate.utentes.outbox.OutboxWriter;
import pt.karate.utentes.repo.AlunoRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Constrói os snapshots dos agregados alterados e grava-os na outbox.
 * Cada agregado é emitido no máximo uma vez por chamada, com uma nova versão.
 */
@Component
public class EventosUtentes {

    private final OutboxWriter outbox;
    private final AlunoRepository alunos;

    public EventosUtentes(OutboxWriter outbox, AlunoRepository alunos) {
        this.outbox = outbox;
        this.alunos = alunos;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emitir(String tipo, Collection<Aluno> alunosAlterados, Collection<Pessoa> pessoasAlteradas) {
        Map<UUID, Aluno> unicosAlunos = new LinkedHashMap<>();
        alunosAlterados.forEach(a -> unicosAlunos.putIfAbsent(a.getId(), a));
        Map<UUID, Pessoa> unicasPessoas = new LinkedHashMap<>();
        pessoasAlteradas.forEach(p -> unicasPessoas.putIfAbsent(p.getId(), p));

        for (Aluno aluno : unicosAlunos.values()) {
            long versao = aluno.proximaVersaoEvento();
            outbox.registar("Aluno", aluno.getId(), versao, "Aluno" + tipo, Topicos.ALUNOS,
                    snapshot(aluno, versao));
        }
        for (Pessoa pessoa : unicasPessoas.values()) {
            long versao = pessoa.proximaVersaoEvento();
            outbox.registar("Pessoa", pessoa.getId(), versao, "Pessoa" + tipo, Topicos.PESSOAS,
                    snapshot(pessoa, versao));
        }
    }

    private AlunoSnapshot snapshot(Aluno aluno, long versao) {
        Pessoa p = aluno.getPessoa();
        var responsaveis = aluno.relacoesVigentes().stream()
                .map(r -> new AlunoSnapshot.ResponsavelResumo(
                        r.getResponsavel().getId(),
                        r.getResponsavel().getNome(),
                        r.getResponsavel().getTelefone(),
                        r.getResponsavel().getEmail(),
                        r.getParentesco().name(),
                        r.isResponsavelLegal(),
                        r.isResponsavelFiscal(),
                        r.isContactoEmergencia(),
                        r.isAutorizadoRecolha()))
                .toList();
        var fiscal = aluno.responsavelFiscal()
                .map(EventosUtentes::resumoFiscal)
                .orElse(null);
        return new AlunoSnapshot(
                aluno.getId(), versao, p.getId(), p.getNome(), p.getDataNascimento(),
                p.getEmail(), p.getTelefone(), aluno.getEstado().name(), aluno.getNumFederacao(),
                aluno.getGraduacaoAtual(), aluno.getDataInscricao(), responsaveis, fiscal);
    }

    private static AlunoSnapshot.ResponsavelFiscalResumo resumoFiscal(RelacaoResponsavel r) {
        DadosFiscais df = r.getResponsavel().getDadosFiscais().orElseThrow();
        return new AlunoSnapshot.ResponsavelFiscalResumo(
                r.getResponsavel().getId(), df.nomeFaturacao(), df.nif(), df.moradaFiscal(),
                r.getParentesco() == Parentesco.PROPRIO);
    }

    private PessoaSnapshot snapshot(Pessoa pessoa, long versao) {
        var associados = alunos.findComResponsavelVigente(pessoa.getId()).stream()
                .flatMap(a -> a.relacoesVigentes().stream()
                        .filter(r -> r.getResponsavel().getId().equals(pessoa.getId()))
                        .map(r -> new PessoaSnapshot.AlunoAssociado(
                                a.getId(), a.getPessoa().getNome(), r.getParentesco().name(),
                                r.isResponsavelFiscal())))
                .toList();
        var df = pessoa.getDadosFiscais();
        return new PessoaSnapshot(
                pessoa.getId(), versao, pessoa.getNome(), pessoa.getDataNascimento(),
                pessoa.getEmail(), pessoa.getTelefone(), pessoa.getMorada(),
                df.map(DadosFiscais::nif).orElse(null),
                df.map(DadosFiscais::nomeFaturacao).orElse(null),
                associados);
    }
}
