package pt.karate.utentes.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.utentes.domain.Aluno;
import pt.karate.utentes.domain.DadosFiscais;
import pt.karate.utentes.domain.NaoEncontradoException;
import pt.karate.utentes.domain.Pessoa;
import pt.karate.utentes.domain.RegraNegocioException;
import pt.karate.utentes.repo.AlunoRepository;
import pt.karate.utentes.repo.PessoaRepository;
import pt.karate.utentes.app.Vistas.PessoaView;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PessoaService {

    public record DadosPessoa(String nome, LocalDate dataNascimento, String email, String telefone, String morada) {
    }

    private final PessoaRepository pessoas;
    private final AlunoRepository alunos;
    private final EventosUtentes eventos;
    private final Clock clock;

    public PessoaService(PessoaRepository pessoas, AlunoRepository alunos, EventosUtentes eventos, Clock clock) {
        this.pessoas = pessoas;
        this.alunos = alunos;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional
    public PessoaView registar(DadosPessoa dados, DadosFiscais dadosFiscais) {
        UUID id = UUID.randomUUID();
        garantirNifLivre(dadosFiscais, id);
        Pessoa pessoa = new Pessoa(id, dados.nome(), dados.dataNascimento(), dados.email(),
                dados.telefone(), dados.morada(), dadosFiscais, clock);
        pessoas.save(pessoa);
        eventos.emitir("Registada", List.of(), List.of(pessoa));
        return Vistas.de(pessoa);
    }

    @Transactional
    public PessoaView atualizar(UUID id, DadosPessoa dados) {
        Pessoa pessoa = carregar(id);
        pessoa.atualizar(dados.nome(), dados.dataNascimento(), dados.email(), dados.telefone(),
                dados.morada(), clock);
        // A data de nascimento pode afetar regras (ex.: responsável fiscal passa a ser menor).
        List<Aluno> afetados = revalidarAlunosAfetados(pessoa);
        eventos.emitir("Atualizada", afetados, List.of(pessoa));
        return Vistas.de(pessoa);
    }

    /** {@code dadosFiscais == null} remove os dados fiscais (falha se a pessoa for responsável fiscal). */
    @Transactional
    public PessoaView definirDadosFiscais(UUID id, DadosFiscais dadosFiscais) {
        Pessoa pessoa = carregar(id);
        garantirNifLivre(dadosFiscais, id);
        pessoa.definirDadosFiscais(dadosFiscais, clock);
        List<Aluno> afetados = revalidarAlunosAfetados(pessoa);
        eventos.emitir("Atualizada", afetados, List.of(pessoa));
        return Vistas.de(pessoa);
    }

    @Transactional(readOnly = true)
    public PessoaView obter(UUID id) {
        return Vistas.de(carregar(id));
    }

    private Pessoa carregar(UUID id) {
        return pessoas.findById(id).orElseThrow(() -> new NaoEncontradoException("Pessoa", id));
    }

    private List<Aluno> revalidarAlunosAfetados(Pessoa pessoa) {
        LocalDate hoje = LocalDate.now(clock);
        List<Aluno> afetados = new ArrayList<>(alunos.findComResponsavelVigente(pessoa.getId()));
        alunos.findByPessoaId(pessoa.getId()).ifPresent(afetados::add);
        afetados.forEach(a -> a.validar(hoje));
        return afetados;
    }

    private void garantirNifLivre(DadosFiscais dadosFiscais, UUID pessoaId) {
        if (dadosFiscais != null && pessoas.nifEmUsoPorOutra(dadosFiscais.nif(), pessoaId)) {
            throw new RegraNegocioException("NIF_EM_USO", "Já existe outra pessoa registada com este NIF.");
        }
    }
}
