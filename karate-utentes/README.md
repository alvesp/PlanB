# karate-utentes

Sistema de uma escola de karate com dois domínios:

- **Utentes**: alunos (na maioria menores), os seus responsáveis e quem responde fiscalmente por cada aluno.
- **Aprendizagem**: graduações, temas (kihon, kata, kumite…), turmas, aulas, presenças, avaliações e exames.

Segue a arquitetura do PlanB: cada serviço tem a sua base de dados relacional com outbox
transacional, publica eventos em Kafka, e um read-side em OpenSearch serve a pesquisa e a leitura rápida.

```
  REST                          REST                                     REST (pesquisa)
   │                             │                                             ▲
   ▼                             ▼                                             │
┌──────────────────┐   ┌──────────────────────┐   ┌─────────┐   ┌─────────────────────────┐
│ utentes-service  │   │ aprendizagem-service │   │  Kafka  │   │  utentes-query-service  │
│ Postgres utentes │   │ Postgres aprendizagem│   │         │   │                         │
│  + outbox ───────┼──▶│                      │   │ alunos  │──▶│ projeção ──▶ OpenSearch │
│                  │   │  + outbox ───────────┼──▶│ pessoas │   │ (versionamento externo) │
│                  │◀──┼── graduações ────────┼───│ progresso   │                         │
└──────────────────┘   └──────────────────────┘   │ graduações  └─────────────────────────┘
        ▲                        ▲                └─────────┘
        └── alunos (réplica) ────┘
```

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `karate-outbox` | Biblioteca partilhada: outbox transacional e relay para Kafka (auto-configuração) |
| `utentes-events` | Contratos `AlunoSnapshot`, `PessoaSnapshot` e tópicos de utentes |
| `aprendizagem-events` | Contratos `GraduacaoAtribuida`, `ProgressoAlunoSnapshot` e tópicos de aprendizagem |
| `utentes-service` | Pessoas, alunos, responsáveis e dados fiscais. Porta 8080 |
| `aprendizagem-service` | Graduações, temas, turmas, aulas, presenças, avaliações e exames. Porta 8082 |
| `utentes-query-service` | Read-side: projeta alunos, pessoas e progresso no OpenSearch. Porta 8081 |

## Utentes

- **Pessoa**: qualquer indivíduo (aluno ou responsável). Pode ter dados fiscais (NIF validado
  com dígito de controlo, nome e morada de faturação).
- **Aluno**: uma pessoa inscrita, com estado, número de federação e graduação atual
  (esta vem do aprendizagem-service).
- **Relação aluno ↔ responsável**: parentesco e papéis (legal, fiscal, contacto de emergência,
  autorizado a recolher). As linhas nunca são alteradas: mudar papéis fecha a linha vigente
  e cria outra, por isso fica o histórico de quem foi responsável fiscal e desde quando.

Regras (em `PoliticaResponsaveis`, verificadas em cada alteração):

1. Cada aluno tem **exatamente um responsável fiscal** vigente (também garantido na BD por
   uma *exclusion constraint* diferida).
2. O responsável fiscal tem de ser **maior de idade** e ter **dados fiscais completos**.
3. Um aluno **menor** tem pelo menos um **responsável legal** (pai, mãe ou tutor) maior de idade.
4. Um aluno **adulto** pode ser responsável por si próprio (parentesco `PROPRIO`).
5. Alterar a data de nascimento ou remover os dados fiscais de uma pessoa revalida todos os
   alunos de que é responsável (falha se os deixar inválidos).

## Aprendizagem

- **Graduação**: código (`8KYU`), nome, cor da faixa, tipo (kyu/dan), ordem de progressão e
  mínimos para exame (presenças e meses desde a graduação anterior). A migração cria uma
  escala de exemplo de 10º Kyu a 3º Dan; ajuste cores e mínimos à sua escola.
- **Tema**: técnica, kata, kumite ou teoria. Se tiver graduação, faz parte do programa de exame dessa graduação.
- **Turma**: grupo de treino com os seus alunos.
- **Aula**: data, hora, duração, instrutor, sumário e temas trabalhados.
- **Presença**: por aula e aluno, com o estado `PRESENTE`, `ATRASADO`, `FALTA` ou
  `FALTA_JUSTIFICADA`. Só presente e atrasado contam para os mínimos.
- **Avaliação de tema**: nível (`INICIADO`, `EM_PROGRESSO`, `DOMINADO`) numa data. Guarda o histórico; conta a mais recente.
- **Exame de graduação**: graduação, data, examinador e resultado. Se for aprovado, passa a
  ser a graduação atual e é publicado `GraduacaoAtribuida`; o utentes-service atualiza então a ficha do aluno.

Regras (em `RequisitosGraduacao`):

1. Só se faz exame à **graduação seguinte**, com os **mínimos cumpridos** à data do exame:
   presenças desde a última graduação e meses desde essa graduação (ou desde a inscrição).
2. A data do exame não pode ser futura nem anterior à da graduação atual.
3. A **dispensa de requisitos** (com motivo obrigatório) permite reconhecer graduações de
   outro clube ou saltar graduações.
4. O domínio dos temas do programa é informativo e não bloqueia o exame.
5. Não se registam presenças em aulas futuras ou canceladas, nem de alunos inativos.
6. Uma aula com presenças não pode ser cancelada nem mudar de data.

O aprendizagem-service mantém uma **réplica dos alunos** (tabela `aluno_ref`), alimentada pelo
tópico compactado `karate.utentes.alunos`. Um aluno acabado de inscrever pode demorar uns
instantes a ficar disponível aqui.

## Eventos e consistência

- Cada alteração grava, **na mesma transação**, o evento na tabela `outbox_evento`. O
  `OutboxRelay` (biblioteca `karate-outbox`) publica no Kafka (`acks=all`, produtor idempotente)
  e só depois marca como publicado. Pode haver várias instâncias (`FOR UPDATE SKIP LOCKED`).
- Entrega **at-least-once**. Todos os consumidores são idempotentes:
  - o read-side indexa com `version_type=external`;
  - a réplica de alunos só aceita versões mais recentes;
  - o utentes-service ignora um `exameId` repetido.
- Tópicos de estado (`alunos`, `pessoas`, `progresso`) são **compactados**: um consumidor novo
  reconstrói tudo lendo desde o início. `graduacoes` é um tópico de factos com retenção de um ano.
- Mensagens ilegíveis vão para uma DLT. Falhas de infraestrutura repetem com backoff até
  1 minuto, sem limite, e o processamento retoma sozinho.

| Tópico | Produtor | Consumidores |
|---|---|---|
| `karate.utentes.alunos` | utentes-service | aprendizagem-service (réplica), query-service |
| `karate.utentes.pessoas` | utentes-service | query-service |
| `karate.aprendizagem.graduacoes` | aprendizagem-service | utentes-service |
| `karate.aprendizagem.progresso` | aprendizagem-service | query-service |

## Arrancar

```bash
docker compose up --build
```

O Postgres cria as bases `utentes` e `aprendizagem` na primeira inicialização. Se já tinha o
volume de uma versão anterior, crie a segunda base manualmente:

```bash
docker compose exec postgres psql -U utentes -c 'CREATE DATABASE aprendizagem'
```

Também pode arrancar só a infraestrutura (`docker compose up postgres kafka opensearch`) e
correr os serviços a partir do IDE; o `application.yml` já aponta para `localhost`.

```bash
mvn verify   # testes unitários + teste de integração com Testcontainers (requer Docker)
```

## Exemplos

```bash
# --- Utentes ---
MAE=$(curl -s localhost:8080/api/pessoas -H 'Content-Type: application/json' -d '{
  "nome": "Ana Silva", "dataNascimento": "1988-03-14", "telefone": "912345678",
  "dadosFiscais": {"nif": "123456789", "nomeFaturacao": "Ana Silva", "moradaFiscal": "Rua A, Porto"}
}' | jq -r .id)

RITA=$(curl -s localhost:8080/api/pessoas -H 'Content-Type: application/json' -d '{
  "nome": "Rita Silva", "dataNascimento": "2017-06-02"
}' | jq -r .id)

ALUNO=$(curl -s localhost:8080/api/alunos -H 'Content-Type: application/json' -d "{
  \"pessoaId\": \"$RITA\",
  \"responsaveis\": [{\"pessoaId\": \"$MAE\", \"papeis\": {
      \"parentesco\": \"MAE\", \"responsavelLegal\": true, \"responsavelFiscal\": true,
      \"contactoEmergencia\": true, \"autorizadoRecolha\": true}}]
}" | jq -r .id)

# --- Aprendizagem ---
# Programa do 9º Kyu: um kata
KATA=$(curl -s localhost:8082/api/temas -H 'Content-Type: application/json' -d '{
  "nome": "Heian Shodan", "categoria": "KATA", "graduacao": "9KYU"
}' | jq -r .id)

TURMA=$(curl -s localhost:8082/api/turmas -H 'Content-Type: application/json' -d '{
  "nome": "Infantis 6-10", "instrutor": "Sensei Kato"
}' | jq -r .id)
curl -s -X PUT localhost:8082/api/turmas/$TURMA/alunos/$ALUNO | jq

# Aula de hoje com o kata trabalhado, e folha de presenças
AULA=$(curl -s localhost:8082/api/aulas -H 'Content-Type: application/json' -d "{
  \"turmaId\": \"$TURMA\", \"data\": \"$(date +%F)\", \"horaInicio\": \"18:00\",
  \"duracaoMinutos\": 60, \"sumario\": \"Introdução ao Heian Shodan\", \"temas\": [\"$KATA\"]
}" | jq -r .id)
curl -s localhost:8082/api/aulas/$AULA/presencas | jq
curl -s -X PUT localhost:8082/api/aulas/$AULA/presencas -H 'Content-Type: application/json' -d "{
  \"registos\": [{\"alunoId\": \"$ALUNO\", \"estado\": \"PRESENTE\"}]
}" | jq

# Primeira graduação (10º Kyu não tem mínimos)
curl -s localhost:8082/api/alunos/$ALUNO/exames -H 'Content-Type: application/json' -d '{
  "graduacao": "10KYU", "examinador": "Sensei Kato", "resultado": "APROVADO"
}' | jq

# Avaliação de um tema, progresso e elegibilidade para o 9º Kyu
curl -s localhost:8082/api/alunos/$ALUNO/avaliacoes -H 'Content-Type: application/json' -d "{
  \"temaId\": \"$KATA\", \"nivel\": \"EM_PROGRESSO\", \"avaliador\": \"Sensei Kato\"
}" | jq
curl -s localhost:8082/api/alunos/$ALUNO/progresso | jq
curl -s localhost:8082/api/alunos/$ALUNO/temas | jq

# --- Read-side ---
curl -s 'localhost:8081/api/consulta/alunos?q=rita&menor=true' | jq
curl -s "localhost:8081/api/consulta/progresso?semPresencaDesde=$(date -d '-30 days' +%F)" | jq
```

## Endpoints

**utentes-service (8080)**

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/pessoas` | Registar pessoa (dados fiscais opcionais) |
| GET / PUT | `/api/pessoas/{id}` | Obter / atualizar dados pessoais |
| PUT / DELETE | `/api/pessoas/{id}/dados-fiscais` | Definir / remover dados fiscais |
| POST | `/api/alunos` | Inscrever aluno com responsáveis |
| GET | `/api/alunos/{id}?historico=true` | Obter aluno (com histórico de relações e graduações) |
| PUT | `/api/alunos/{id}` | Número de federação e estado |
| POST | `/api/alunos/{id}/responsaveis` | Adicionar responsável |
| PUT / DELETE | `/api/alunos/{id}/responsaveis/{pessoaId}` | Alterar papéis / terminar relação |
| GET / PUT | `/api/alunos/{id}/responsavel-fiscal` | Consultar / transferir responsabilidade fiscal |

**aprendizagem-service (8082)**

| Método | Caminho | Descrição |
|---|---|---|
| GET / POST | `/api/graduacoes` | Listar / criar graduações |
| PUT | `/api/graduacoes/{codigo}` | Alterar nome, cor, ordem, mínimos ou desativar |
| GET | `/api/graduacoes/{codigo}/programa` | Temas do programa de exame |
| GET / POST | `/api/temas` | Pesquisar (`categoria`, `graduacao`) / criar temas |
| GET / PUT | `/api/temas/{id}` | Obter / alterar tema |
| GET / POST | `/api/turmas` | Listar / criar turmas |
| GET / PUT | `/api/turmas/{id}` | Obter / alterar turma |
| PUT / DELETE | `/api/turmas/{id}/alunos/{alunoId}` | Juntar / retirar aluno |
| GET / POST | `/api/aulas` | Pesquisar (`turmaId`, `de`, `ate`) / criar aula |
| GET / PUT | `/api/aulas/{id}` | Obter / alterar aula (horário, sumário, temas) |
| POST | `/api/aulas/{id}/cancelar` | Cancelar aula sem presenças |
| GET / PUT | `/api/aulas/{id}/presencas` | Folha de presenças / registar presenças em lote |
| GET | `/api/alunos/{id}/progresso` | Graduação atual e seguinte, presenças, elegibilidade, exames, avaliações |
| GET | `/api/alunos/{id}/elegibilidade` | Requisitos para a próxima graduação |
| GET | `/api/alunos/{id}/presencas` | Registo de presenças (`de`, `ate`) |
| GET | `/api/alunos/{id}/temas` | Temas da próxima graduação (`todos=true` para todos), com nível e prática |
| GET / POST | `/api/alunos/{id}/avaliacoes` | Histórico / nova avaliação de tema |
| GET / POST | `/api/alunos/{id}/exames` | Histórico / registar exame |

**utentes-query-service (8081)**

| Método | Caminho | Filtros |
|---|---|---|
| GET | `/api/consulta/alunos` | `q` (nome do aluno ou responsável, nº federação, NIF), `estado`, `graduacao`, `menor`, `responsavelId` |
| GET | `/api/consulta/alunos/{id}` | |
| GET | `/api/consulta/pessoas` | `q`, `nif` |
| GET | `/api/consulta/pessoas/{id}` | |
| GET | `/api/consulta/progresso` | `q`, `graduacao` (código), `semPresencaDesde` (data) |
| GET | `/api/consulta/progresso/{alunoId}` | |

As pesquisas aceitam `pagina` e `tamanho`. Os erros seguem o RFC 9457
(`application/problem+json`); violações de regras devolvem 422 com `codigo`.

## Pendente / próximos passos

- **Mensalidades e faturação**: cobranças por aluno faturadas ao responsável fiscal, com
  integração com software de faturação certificado pela AT.
- **Anulação de exames**: hoje um exame registado não se corrige; falta um evento
  `GraduacaoAnulada` para o utentes-service desfazer a graduação.
- **Spring Cloud**: gateway à frente dos serviços, config server e service discovery.
- **Segurança**: autenticação (ex.: OAuth2/Keycloak) e perfis (secretaria, treinador, examinador).
- **RGPD**: consentimentos, dados de saúde com acesso restrito e direito ao apagamento
  (*tombstones* nos tópicos compactados).
- **Maioridade**: aviso quando um aluno faz 18 anos, para transferir a responsabilidade fiscal.
