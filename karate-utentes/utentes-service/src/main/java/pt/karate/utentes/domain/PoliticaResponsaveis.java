package pt.karate.utentes.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Regras sobre quem pode ser responsável (legal e fiscal) por um aluno.
 * <p>
 * Classe sem dependências de framework: recebe uma vista simples do estado e lança
 * {@link RegraNegocioException} na primeira regra violada.
 *
 * <ol>
 *   <li>Cada aluno tem exatamente um responsável fiscal vigente.</li>
 *   <li>O mesmo responsável não pode aparecer duas vezes.</li>
 *   <li>{@code PROPRIO} só existe quando o responsável é o próprio aluno, e só se for maior.</li>
 *   <li>Um aluno menor tem pelo menos um responsável legal (pai, mãe ou tutor) maior de idade.</li>
 *   <li>Responsáveis legais e fiscais têm de ser maiores de idade.</li>
 *   <li>O responsável fiscal tem de ter dados fiscais (NIF, nome e morada fiscal).</li>
 * </ol>
 */
public final class PoliticaResponsaveis {

    public static final int MAIORIDADE = 18;

    public record PessoaInfo(UUID id, LocalDate dataNascimento, boolean temDadosFiscais) {
    }

    public record RelacaoInfo(PessoaInfo responsavel, Parentesco parentesco, boolean legal, boolean fiscal) {
    }

    private PoliticaResponsaveis() {
    }

    public static boolean maiorDeIdade(LocalDate dataNascimento, LocalDate hoje) {
        return Period.between(dataNascimento, hoje).getYears() >= MAIORIDADE;
    }

    public static void validar(PessoaInfo aluno, List<RelacaoInfo> vigentes, LocalDate hoje) {
        boolean alunoMaior = maiorDeIdade(aluno.dataNascimento(), hoje);

        Set<UUID> vistos = new HashSet<>();
        for (RelacaoInfo r : vigentes) {
            if (!vistos.add(r.responsavel().id())) {
                throw new RegraNegocioException("RESPONSAVEL_DUPLICADO",
                        "A mesma pessoa não pode ter duas relações vigentes com o aluno.");
            }
            validarRelacao(aluno, alunoMaior, r, hoje);
        }

        long fiscais = vigentes.stream().filter(RelacaoInfo::fiscal).count();
        if (fiscais != 1) {
            throw new RegraNegocioException("RESPONSAVEL_FISCAL_UNICO",
                    "O aluno tem de ter exatamente um responsável fiscal (encontrados: " + fiscais + ").");
        }

        if (!alunoMaior && vigentes.stream().noneMatch(RelacaoInfo::legal)) {
            throw new RegraNegocioException("MENOR_SEM_RESPONSAVEL_LEGAL",
                    "Um aluno menor de idade tem de ter pelo menos um responsável legal.");
        }
    }

    private static void validarRelacao(PessoaInfo aluno, boolean alunoMaior, RelacaoInfo r, LocalDate hoje) {
        boolean eOProprio = r.responsavel().id().equals(aluno.id());

        if (eOProprio != (r.parentesco() == Parentesco.PROPRIO)) {
            throw new RegraNegocioException("PARENTESCO_PROPRIO_INVALIDO",
                    "O parentesco PROPRIO só se aplica quando o responsável é o próprio aluno.");
        }
        if (eOProprio && !alunoMaior) {
            throw new RegraNegocioException("MENOR_NAO_PODE_SER_RESPONSAVEL",
                    "Um aluno menor de idade não pode ser responsável por si próprio.");
        }

        boolean responsavelMaior = maiorDeIdade(r.responsavel().dataNascimento(), hoje);

        if (r.legal()) {
            if (!r.parentesco().podeSerResponsavelLegal()) {
                throw new RegraNegocioException("PARENTESCO_NAO_PERMITE_RESPONSABILIDADE_LEGAL",
                        "Só pai, mãe ou tutor podem ser responsáveis legais (recebido: " + r.parentesco() + ").");
            }
            if (!responsavelMaior) {
                throw new RegraNegocioException("RESPONSAVEL_LEGAL_MENOR",
                        "O responsável legal tem de ser maior de idade.");
            }
        }

        if (r.fiscal()) {
            if (!responsavelMaior) {
                throw new RegraNegocioException("RESPONSAVEL_FISCAL_MENOR",
                        "O responsável fiscal tem de ser maior de idade.");
            }
            if (!r.responsavel().temDadosFiscais()) {
                throw new RegraNegocioException("RESPONSAVEL_FISCAL_SEM_DADOS",
                        "O responsável fiscal tem de ter NIF, nome de faturação e morada fiscal.");
            }
        }
    }
}
