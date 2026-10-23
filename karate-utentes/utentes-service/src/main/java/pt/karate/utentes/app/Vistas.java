package pt.karate.utentes.app;

import pt.karate.utentes.domain.Aluno;
import pt.karate.utentes.domain.DadosFiscais;
import pt.karate.utentes.domain.GraduacaoRegisto;
import pt.karate.utentes.domain.Pessoa;
import pt.karate.utentes.domain.RelacaoResponsavel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Representações devolvidas pela API do write-side, construídas dentro da transação
 * (open-in-view está desligado).
 */
public final class Vistas {

    private Vistas() {
    }

    public record PessoaView(UUID id, String nome, LocalDate dataNascimento, String email, String telefone,
                             String morada, DadosFiscaisView dadosFiscais, long versao) {
    }

    public record DadosFiscaisView(String nif, String nomeFaturacao, String moradaFiscal) {
    }

    public record RelacaoView(UUID pessoaId, String nome, String parentesco, boolean responsavelLegal,
                              boolean responsavelFiscal, boolean contactoEmergencia, boolean autorizadoRecolha,
                              LocalDate validoDesde, LocalDate validoAte) {
    }

    public record GraduacaoView(String graduacao, LocalDate dataExame, String examinador) {
    }

    public record AlunoView(UUID id, UUID pessoaId, String nome, LocalDate dataNascimento, String estado,
                            String numFederacao, String graduacaoAtual, LocalDate dataInscricao,
                            List<RelacaoView> responsaveis, List<GraduacaoView> graduacoes, long versao) {
    }

    public record ResponsavelFiscalView(UUID pessoaId, String nome, String parentesco, String nif,
                                        String nomeFaturacao, String moradaFiscal, LocalDate desde) {
    }

    public static PessoaView de(Pessoa p) {
        return new PessoaView(p.getId(), p.getNome(), p.getDataNascimento(), p.getEmail(), p.getTelefone(),
                p.getMorada(), p.getDadosFiscais().map(Vistas::de).orElse(null), p.getVersaoEvento());
    }

    public static DadosFiscaisView de(DadosFiscais df) {
        return new DadosFiscaisView(df.nif(), df.nomeFaturacao(), df.moradaFiscal());
    }

    public static RelacaoView de(RelacaoResponsavel r) {
        return new RelacaoView(r.getResponsavel().getId(), r.getResponsavel().getNome(), r.getParentesco().name(),
                r.isResponsavelLegal(), r.isResponsavelFiscal(), r.isContactoEmergencia(), r.isAutorizadoRecolha(),
                r.getValidoDesde(), r.getValidoAte());
    }

    public static GraduacaoView de(GraduacaoRegisto g) {
        return new GraduacaoView(g.getGraduacao(), g.getDataExame(), g.getExaminador());
    }

    /** {@code historico=true} inclui relações já terminadas. */
    public static AlunoView de(Aluno a, boolean historico) {
        List<RelacaoResponsavel> relacoes = historico ? a.getRelacoes() : a.relacoesVigentes();
        return new AlunoView(a.getId(), a.getPessoa().getId(), a.getPessoa().getNome(),
                a.getPessoa().getDataNascimento(), a.getEstado().name(), a.getNumFederacao(),
                a.getGraduacaoAtual(), a.getDataInscricao(),
                relacoes.stream().map(Vistas::de).toList(),
                a.getGraduacoes().stream().map(Vistas::de).toList(),
                a.getVersaoEvento());
    }

    public static ResponsavelFiscalView fiscal(RelacaoResponsavel r) {
        DadosFiscais df = r.getResponsavel().getDadosFiscais().orElseThrow();
        return new ResponsavelFiscalView(r.getResponsavel().getId(), r.getResponsavel().getNome(),
                r.getParentesco().name(), df.nif(), df.nomeFaturacao(), df.moradaFiscal(), r.getValidoDesde());
    }
}
