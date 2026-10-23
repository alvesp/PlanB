# karate-utentes

Sistema de uma escola de karate com dois domínios:

- **Utentes**: alunos (na maioria menores), os seus responsáveis e quem responde fiscalmente por cada aluno.
- **Aprendizagem**: graduações, temas (kihon, kata, kumite…), turmas, aulas, presenças, avaliações e exames.

Segue a arquitetura do PlanB com **CQRS**:

- **Comandos**: cada serviço de escrita tem a sua base de dados relacional, com um utilizador próprio.
- **Eventos**: as alterações são publicadas em Kafka através de uma outbox transacional.
- **Consultas**: todas as leituras são feitas num read-side em OpenSearch.

```
          COMANDOS (POST/PUT/DELETE)                               CONSULTAS (GET)
          │                       │                                       ▲
          ▼                       ▼                                       │
┌──────────────────┐   ┌──────────────────────┐   ┌─────────┐   ┌─────────────────────────┐
│ utentes-service  │   │ aprendizagem-service │   │  Kafka  │   │  utentes-query-service  │
│ BD utentes       │   │ BD aprendizagem      │   │         │   │                         │
│ (utentes_app)    │   │ (aprendizagem_app)   │   │snapshots│──▶│ projeções ──▶ OpenSearch│
│  + outbox ───────┼──▶│  + outbox ───────────┼──▶│         │   │ (versionamento externo) │
│                  │◀──┼── graduações ────────┼───│         │   │                         │
└──────────────────┘   └──────────────────────┘   └─────────┘   └─────────────────────────┘
        ▲                        ▲
        └── alunos (réplica) ────┘
```

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `karate-outbox` | Biblioteca partilhada: outbox transacional e relay para Kafka (auto-configuração) |
| `utentes-events` | Contratos `AlunoSnapshot`, `PessoaSnapshot` e tópicos de utentes |
| `aprendizagem-events` | Contratos dos snapshots de aprendizagem, `GraduacaoAtribuida` e tópicos |
| `utentes-service` | Comandos: pessoas, alunos, responsáveis e dados fiscais. Porta 8080 |
| `aprendizagem-service` | Comandos: graduações, temas, turmas, aulas, presenças, avaliações e exames. Porta 8082 |
| `utentes-query-service` | Consultas: projeta todos os snapshots no OpenSearch e serve as leituras. Porta 8081 |

## CQRS

- **Os serviços de escrita só aceitam comandos.** Validam as regras contra a sua base de dados
  e respondem com `{ "id": ..., "versao": ... }`, sem dados de leitura. As violações de regras
  continuam a ser síncronas (422 com `codigo`).
- **Cada alteração publica o snapshot completo do agregado** (aluno, pessoa, progresso,
  graduação, tema, turma, aula) na mesma transação, através da outbox.
- **O read-side é a única fonte de leituras.** Cada snapshot é indexado com
  `version_type=external`, por isso duplicados e eventos fora de ordem são ignorados.
- **Ler as próprias escritas:** o `versao` devolvido pelo comando é o mesmo campo `versao` do
  documento no read-side. O cliente pode esperar até `documento.versao >= versao`. Nas
  avaliações e nos exames, a versão devolvida é a do progresso do aluno.
- **O que depende da data de hoje calcula-se na leitura.** É o caso da elegibilidade para exame:
  o snapshot de progresso publica os requisitos (`presencasMinimas`, `cumpreTempoEm`) e o
  read-side compara-os com a data atual. Assim, um aluno fica elegível com o passar do tempo
  sem ser preciso um novo evento.
- **Os nomes de alunos em turmas e folhas de presenças** juntam-se na leitura a partir do
  índice de alunos, para não ficarem desatualizados.
- **Reconstruir o read-side:** os tópicos de estado são compactados. Basta apagar os índices
  e arrancar com outro `utentes.consulta.grupo` para reprocessar tudo desde o início.

| Tópico | Produtor | Consumidores |
|---|---|---|
| `karate.utentes.alunos` | utentes-service | aprendizagem-service (réplica), query-service |
| `karate.utentes.pessoas` | utentes-service | query-service |
| `karate.aprendizagem.graduacoes` (factos) | aprendizagem-service | utentes-service |
| `karate.aprendizagem.progresso` | aprendizagem-service | query-service |
| `karate.aprendizagem.catalogo.graduacoes` | aprendizagem-service | query-service |
| `karate.aprendizagem.temas` | aprendizagem-service | query-service |
| `karate.aprendizagem.turmas` | aprendizagem-service | query-service |
| `karate.aprendizagem.aulas` | aprendizagem-service | query-service |

## Bases de dados e utilizadores

Cada serviço de escrita tem **a sua base de dados e o seu utilizador**:

| Base | Utilizador | Serviço |
|---|---|---|
| `utentes` | `utentes_app` | utentes-service |
| `aprendizagem` | `aprendizagem_app` | aprendizagem-service |

- O script `docker/postgres/01-bases-de-dados.sh` cria os utilizadores e as bases na primeira
  inicialização. Cada utilizador é dono só da sua base, e o `CONNECT` está revogado a `PUBLIC`:
  um serviço não consegue ligar-se à base do outro.
- O superutilizador `postgres` só é usado por esse script.
- As credenciais vêm de variáveis de ambiente. O ficheiro `.env` tem valores de
  desenvolvimento; em produção, defina-os no ambiente ou num gestor de segredos.

> **Volume de uma versão anterior:** os utilizadores só são criados num volume novo. Recrie-o
> com `docker compose down -v` (apaga os dados de desenvolvimento).

## Regras de negócio

**Utentes** (`PoliticaResponsaveis`):

1. Cada aluno tem **exatamente um responsável fiscal** vigente (também garantido na BD).
2. O responsável fiscal tem de ser **maior de idade** e ter **dados fiscais completos** (NIF válido).
3. Um aluno **menor** tem pelo menos um **responsável legal** (pai, mãe ou tutor) maior de idade.
4. Um aluno **adulto** pode ser responsável por si próprio (parentesco `PROPRIO`).
5. As mudanças de papéis fecham a relação vigente e criam outra, por isso fica o histórico.

**Aprendizagem** (`RequisitosGraduacao`):

1. Só se faz exame à **graduação seguinte**, com os **mínimos cumpridos** à data do exame:
   presenças e meses desde a última graduação (ou desde a inscrição).
2. A **dispensa de requisitos** (com motivo) permite reconhecer graduações de outro clube ou saltar graduações.
3. O domínio dos temas do programa é informativo e não bloqueia o exame.
4. Não se registam presenças em aulas futuras ou canceladas, nem de alunos inativos.
5. Uma aula com presenças não pode ser cancelada nem mudar de data.
6. Um exame aprovado publica `GraduacaoAtribuida`, e o utentes-service atualiza a ficha do aluno
   (de forma idempotente, pelo `exameId`).

## Arrancar

```bash
docker compose up --build
mvn verify   # testes unitários + teste de integração com Testcontainers (requer Docker)
```

Também pode arrancar só a infraestrutura (`docker compose up postgres kafka opensearch`) e
correr os serviços no IDE; o `application.yml` já aponta para `localhost` e usa as mesmas
credenciais de desenvolvimento.

## Exemplos

```bash
W=localhost:8080; A=localhost:8082; Q=localhost:8081/api/consulta

# Comandos: devolvem {id, versao}
MAE=$(curl -s $W/api/pessoas -H 'Content-Type: application/json' -d '{
  "nome": "Ana Silva", "dataNascimento": "1988-03-14",
  "dadosFiscais": {"nif": "123456789", "nomeFaturacao": "Ana Silva", "moradaFiscal": "Rua A, Porto"}
}' | jq -r .id)
RITA=$(curl -s $W/api/pessoas -H 'Content-Type: application/json' -d '{
  "nome": "Rita Silva", "dataNascimento": "2017-06-02"}' | jq -r .id)
ALUNO=$(curl -s $W/api/alunos -H 'Content-Type: application/json' -d "{
  \"pessoaId\": \"$RITA\",
  \"responsaveis\": [{\"pessoaId\": \"$MAE\", \"papeis\": {\"parentesco\": \"MAE\",
      \"responsavelLegal\": true, \"responsavelFiscal\": true}}]}" | jq -r .id)

KATA=$(curl -s $A/api/temas -H 'Content-Type: application/json' -d '{
  "nome": "Heian Shodan", "categoria": "KATA", "graduacao": "9KYU"}' | jq -r .id)
TURMA=$(curl -s $A/api/turmas -H 'Content-Type: application/json' -d '{
  "nome": "Infantis 6-10", "instrutor": "Sensei Kato"}' | jq -r .id)
curl -s -X PUT $A/api/turmas/$TURMA/alunos/$ALUNO
AULA=$(curl -s $A/api/aulas -H 'Content-Type: application/json' -d "{
  \"turmaId\": \"$TURMA\", \"data\": \"$(date +%F)\", \"horaInicio\": \"18:00\",
  \"duracaoMinutos\": 60, \"temas\": [\"$KATA\"]}" | jq -r .id)
curl -s -X PUT $A/api/aulas/$AULA/presencas -H 'Content-Type: application/json' -d "{
  \"registos\": [{\"alunoId\": \"$ALUNO\", \"estado\": \"PRESENTE\"}]}"
curl -s $A/api/alunos/$ALUNO/exames -H 'Content-Type: application/json' -d '{
  "graduacao": "10KYU", "examinador": "Sensei Kato", "resultado": "APROVADO"}'

# Consultas (read-side)
curl -s $Q/alunos/$ALUNO | jq                     # ficha, responsáveis, graduações
curl -s $Q/alunos/$ALUNO/responsavel-fiscal | jq
curl -s $Q/aulas/$AULA/presencas | jq              # folha de presenças
curl -s $Q/progresso/$ALUNO | jq
curl -s $Q/progresso/$ALUNO/elegibilidade | jq
curl -s $Q/progresso/$ALUNO/temas | jq
curl -s "$Q/progresso?semPresencaDesde=$(date -d '-30 days' +%F)" | jq
```

## Endpoints

### Comandos

**utentes-service (8080)**

| Método | Caminho | Comando |
|---|---|---|
| POST | `/api/pessoas` | Registar pessoa (dados fiscais opcionais) |
| PUT | `/api/pessoas/{id}` | Atualizar dados pessoais |
| PUT / DELETE | `/api/pessoas/{id}/dados-fiscais` | Definir / remover dados fiscais |
| POST | `/api/alunos` | Inscrever aluno com responsáveis |
| PUT | `/api/alunos/{id}` | Número de federação e estado |
| POST | `/api/alunos/{id}/responsaveis` | Adicionar responsável |
| PUT / DELETE | `/api/alunos/{id}/responsaveis/{pessoaId}` | Alterar papéis / terminar relação |
| PUT | `/api/alunos/{id}/responsavel-fiscal` | Transferir responsabilidade fiscal |

**aprendizagem-service (8082)**

| Método | Caminho | Comando |
|---|---|---|
| POST | `/api/graduacoes` | Criar graduação |
| PUT | `/api/graduacoes/{codigo}` | Alterar nome, cor, ordem, mínimos ou desativar |
| POST | `/api/temas` | Criar tema |
| PUT | `/api/temas/{id}` | Alterar tema |
| POST | `/api/turmas` | Criar turma |
| PUT | `/api/turmas/{id}` | Alterar turma |
| PUT / DELETE | `/api/turmas/{id}/alunos/{alunoId}` | Juntar / retirar aluno |
| POST | `/api/aulas` | Criar aula |
| PUT | `/api/aulas/{id}` | Alterar horário, sumário e temas |
| POST | `/api/aulas/{id}/cancelar` | Cancelar aula sem presenças |
| PUT | `/api/aulas/{id}/presencas` | Registar presenças em lote |
| POST | `/api/alunos/{id}/avaliacoes` | Avaliar tema |
| POST | `/api/alunos/{id}/exames` | Registar exame |

### Consultas — utentes-query-service (8081), prefixo `/api/consulta`

| Caminho | Descrição |
|---|---|
| `/alunos` | Pesquisa: `q` (nome do aluno ou responsável, nº federação, NIF), `estado`, `graduacao`, `menor`, `responsavelId` |
| `/alunos/{id}` | Ficha: responsáveis vigentes e históricos, responsável fiscal, graduações |
| `/alunos/{id}/responsavel-fiscal` | Quem é faturado |
| `/pessoas`, `/pessoas/{id}` | Pesquisa (`q`, `nif`) e ficha |
| `/graduacoes`, `/graduacoes/{codigo}` | Catálogo de graduações |
| `/graduacoes/{codigo}/programa` | Temas do programa de exame |
| `/temas`, `/temas/{id}` | Pesquisa (`q`, `categoria`, `graduacao`, `incluirInativos`) |
| `/turmas`, `/turmas/{id}` | Turmas com os nomes atuais dos alunos |
| `/aulas`, `/aulas/{id}` | Aulas por período (`turmaId`, `de`, `ate`) |
| `/aulas/{id}/presencas` | Folha de presenças |
| `/progresso` | Pesquisa: `q`, `graduacao`, `semPresencaDesde` |
| `/progresso/{alunoId}` | Progresso completo |
| `/progresso/{alunoId}/elegibilidade` | Requisitos para a próxima graduação, à data de hoje |
| `/progresso/{alunoId}/temas` | Programa da próxima graduação (`todos=true` para todos) com nível e prática |
| `/progresso/{alunoId}/presencas` | Registo de presenças (`de`, `ate`) |
| `/progresso/{alunoId}/exames`, `/avaliacoes` | Históricos |

As pesquisas paginadas aceitam `pagina` e `tamanho`. Os erros seguem o RFC 9457
(`application/problem+json`).

## Pendente / próximos passos

- **Mensalidades e faturação** ao responsável fiscal, com software de faturação certificado pela AT.
- **Anulação de exames** (evento `GraduacaoAnulada`).
- **Spring Cloud Gateway** à frente dos serviços, para expor comandos e consultas num único endereço.
- **Segurança**: autenticação (OAuth2/Keycloak) e perfis; separar também o utilizador das
  migrações (dono do esquema) do utilizador da aplicação (só DML).
- **RGPD**: consentimentos, dados de saúde com acesso restrito e direito ao apagamento (*tombstones*).
