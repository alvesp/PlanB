# karate-utentes

Gestão de utentes de uma escola de karate: alunos (na maioria menores), os seus responsáveis
e quem responde fiscalmente por cada aluno.

Segue a arquitetura do PlanoB: write-side relacional com outbox transacional, eventos em Kafka
e read-side em OpenSearch para pesquisa e leitura rápida.

```
            REST (escrita)                                   REST (leitura/pesquisa)
                 │                                                     ▲
                 ▼                                                     │
        ┌──────────────────┐     ┌───────────┐     ┌────────────────────────────┐
        │ utentes-service  │     │   Kafka   │     │   utentes-query-service    │
        │  (Spring Boot)   │     │ (tópicos  │     │      (Spring Boot)         │
        │                  │     │compactados)│    │                            │
        │ Postgres         │────▶│ alunos    │────▶│ projeção idempotente ──▶ OpenSearch
        │  + outbox_evento │relay│ pessoas   │     │ (versionamento externo)    │
        └──────────────────┘     └───────────┘     └────────────────────────────┘
```

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `utentes-events` | Contratos dos eventos (`AlunoSnapshot`, `PessoaSnapshot`) e nomes dos tópicos |
| `utentes-service` | Write-side: pessoas, alunos, relações com responsáveis, dados fiscais, graduações. Postgres + Flyway + outbox + relay para Kafka. Porta 8080 |
| `utentes-query-service` | Read-side: consome os tópicos, projeta no OpenSearch e expõe pesquisa. Porta 8081 |

## Modelo e regras

- **Pessoa**: qualquer indivíduo (aluno ou responsável). Pode ter dados fiscais (NIF validado
  com dígito de controlo, nome e morada de faturação).
- **Aluno**: uma pessoa inscrita, com estado, número de federação e graduações.
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

Casos cobertos: irmãos com o mesmo responsável, pais separados (um legal, outro fiscal),
avós que pagam, aluno adulto, e transferência de responsabilidade fiscal (ex.: aos 18 anos).

## Eventos e consistência

- Cada alteração grava, **na mesma transação**, um snapshot completo do agregado na tabela
  `outbox_evento`. O `OutboxRelay` publica-os no Kafka (`acks=all`, produtor idempotente) e só
  depois os marca como publicados. Várias instâncias podem correr em paralelo
  (`FOR UPDATE SKIP LOCKED`).
- Entrega **at-least-once**. O read-side indexa com `version_type=external`, por isso
  duplicados e eventos fora de ordem são ignorados e o índice converge para o último snapshot.
- Os tópicos são **compactados**: um consumidor novo (outro `group-id`) reconstrói o OpenSearch
  do zero lendo desde o início.
- Mensagens ilegíveis vão para `<tópico>.DLT`. Se o OpenSearch estiver em baixo, a projeção
  repete com backoff até 1 minuto, sem limite, e retoma sozinha.
- O read-side é eventualmente consistente. As respostas do write-side incluem `versao`; o mesmo
  campo existe nos documentos do OpenSearch, para o cliente saber se a projeção já apanhou a escrita.

## Arrancar

```bash
docker compose up --build
```

Ou só a infraestrutura (`docker compose up postgres kafka opensearch`) e os serviços a partir do IDE
(`application.yml` já aponta para `localhost`).

```bash
mvn verify   # testes unitários + teste de integração com Testcontainers (requer Docker)
```

## Exemplos

```bash
# 1. Mãe, com dados fiscais
MAE=$(curl -s localhost:8080/api/pessoas -H 'Content-Type: application/json' -d '{
  "nome": "Ana Silva", "dataNascimento": "1988-03-14", "telefone": "912345678",
  "dadosFiscais": {"nif": "123456789", "nomeFaturacao": "Ana Silva", "moradaFiscal": "Rua A, Porto"}
}' | jq -r .id)

# 2. Filha (menor, sem dados fiscais)
RITA=$(curl -s localhost:8080/api/pessoas -H 'Content-Type: application/json' -d '{
  "nome": "Rita Silva", "dataNascimento": "2017-06-02"
}' | jq -r .id)

# 3. Inscrição com a mãe como responsável legal e fiscal
ALUNO=$(curl -s localhost:8080/api/alunos -H 'Content-Type: application/json' -d "{
  \"pessoaId\": \"$RITA\", \"graduacaoAtual\": \"10 kyu\",
  \"responsaveis\": [{\"pessoaId\": \"$MAE\", \"papeis\": {
      \"parentesco\": \"MAE\", \"responsavelLegal\": true, \"responsavelFiscal\": true,
      \"contactoEmergencia\": true, \"autorizadoRecolha\": true}}]
}" | jq -r .id)

# 4. Quem é faturado
curl -s localhost:8080/api/alunos/$ALUNO/responsavel-fiscal | jq

# 5. Pesquisa no read-side (sem acentos, com tolerância a erros)
curl -s 'localhost:8081/api/consulta/alunos?q=rita&menor=true' | jq
curl -s "localhost:8081/api/consulta/alunos?responsavelId=$MAE" | jq
curl -s 'localhost:8081/api/consulta/pessoas?nif=123456789' | jq
```

### Endpoints

**utentes-service (8080)**

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/pessoas` | Registar pessoa (dados fiscais opcionais) |
| GET / PUT | `/api/pessoas/{id}` | Obter / atualizar dados pessoais |
| PUT / DELETE | `/api/pessoas/{id}/dados-fiscais` | Definir / remover dados fiscais |
| POST | `/api/alunos` | Inscrever aluno com responsáveis |
| GET | `/api/alunos/{id}?historico=true` | Obter aluno (com histórico de relações) |
| PUT | `/api/alunos/{id}` | Número de federação e estado |
| POST | `/api/alunos/{id}/responsaveis` | Adicionar responsável |
| PUT / DELETE | `/api/alunos/{id}/responsaveis/{pessoaId}` | Alterar papéis / terminar relação |
| GET / PUT | `/api/alunos/{id}/responsavel-fiscal` | Consultar / transferir responsabilidade fiscal |
| POST | `/api/alunos/{id}/graduacoes` | Registar exame de graduação |

Erros seguem RFC 9457 (`application/problem+json`); violações de regras devolvem 422 com `codigo`.

**utentes-query-service (8081)**

| Método | Caminho | Filtros |
|---|---|---|
| GET | `/api/consulta/alunos` | `q` (nome do aluno ou responsável, nº federação, NIF), `estado`, `graduacao`, `menor`, `responsavelId`, `pagina`, `tamanho` |
| GET | `/api/consulta/alunos/{id}` | |
| GET | `/api/consulta/pessoas` | `q`, `nif`, `pagina`, `tamanho` |
| GET | `/api/consulta/pessoas/{id}` | |

## Pendente / próximos passos

- **Mensalidades e faturação**: gerar cobranças por aluno faturadas ao responsável fiscal
  (guardando cópia dos dados fiscais usados) e integrar com software de faturação certificado
  pela AT através de API.
- **Spring Cloud**: gateway à frente dos dois serviços, config server e service discovery.
- **Segurança**: autenticação (ex.: OAuth2/Keycloak) e perfis (secretaria vs. treinador);
  o read-side expõe NIF e contactos, por isso tem de ficar atrás de autenticação.
- **RGPD**: consentimentos (dados de menores, fotografias), dados de saúde (exame médico
  desportivo) com acesso restrito, e direito ao apagamento. Com tópicos compactados, apagar
  uma pessoa exige publicar *tombstones* (o read-side já os trata).
- **Maioridade**: tarefa agendada que avisa quando um aluno faz 18 anos, para transferir a
  responsabilidade fiscal para o próprio.
