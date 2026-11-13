# A6-111 — Intercorrências de RH com aprovação de outra pessoa

## Decisão e escopo

Opção A: reutilizar `SolicitacaoAlteracao` da A6-110/A6-80 (PR #6).
O backend concentra criação, autorização, decisão e gravação. Não há mudança de
schema, status nos eventos importados ou cálculo de apuração nesta tarefa.
Frontend (A6-112), registro pelo chat (A6-124) e aplicação das regras de apuração
(A6-108) são integrações separadas.

## Rotas

| Método e rota | Perfil | Resultado |
|---|---|---|
| `POST /api/correcoes` | GERENTE ou SUPERVISOR | Solicitação pendente, HTTP 201 |
| `GET /api/correcoes/minhas` | GERENTE ou SUPERVISOR | Todos os pedidos do autor, mais recentes primeiro |
| `GET /api/correcoes/fila` | SUPERVISOR | Pendentes de outros autores, mais antigos primeiro |
| `GET /api/correcoes/{id}` | Autor ou SUPERVISOR | Detalhe e decisão, quando houver |
| `POST /api/correcoes/{id}/decidir` | SUPERVISOR diferente do autor | Aprova ou reprova |

Todas as rotas exigem Bearer token. ADMIN não recebe permissão de decisão por ser
administrador. O autor nunca vem do corpo da requisição.

## Contrato de RH

Usam a mesma solicitação e as mesmas rotas de correção da base (opção A).
GERENTE e SUPERVISOR podem abrir; somente outro SUPERVISOR pode decidir.

| `tipo` na API | `tipo` em `evento_rh` |
|---|---|
| `AFASTAMENTO` | `afastamento` |
| `FERIAS` | `ferias` |
| `LICENCA_MATERNIDADE` | `licenca_maternidade` |
| `COBERTURA_LOJA` | `cobertura_loja` |
| `DEMISSAO` | `demissao` |

**`POST /api/correcoes`** — exemplo de cobertura:

```json
{
  "tipo": "COBERTURA_LOJA",
  "matricula": "MATRIC-38",
  "competencia": "2026-11",
  "antes": null,
  "depois": {
    "data_inicio": "2026-11-13",
    "data_fim": "2026-11-20",
    "cod_loja": "8"
  },
  "motivo": "Cobertura durante as férias do gerente da loja",
  "origem": "USUARIO"
}
```

- `matricula` deve existir. Para RH não é exigida alocação mensal em `funcionario_loja`.
- `competencia` mantém o formato obrigatório `YYYY-MM` do envelope de solicitação.
  O período efetivo do evento é definido por `data_inicio` e `data_fim`.
- `antes` deve ser ausente ou `null`; `depois` é um objeto, e não texto contendo JSON.
- As duas datas são obrigatórias (`YYYY-MM-DD`), com fim igual ou posterior ao início.
  Para registrar uma demissão pontual, informe a mesma data em ambos os campos.
- `cod_loja` é o código de negócio de uma loja existente, obrigatório somente em
  `COBERTURA_LOJA`. Nos outros tipos deve ser omitido ou `null`.
- `motivo` é obrigatório e não pode ser vazio. Campos desconhecidos dentro de `depois`
  são recusados. Falhas nessas validações retornam **400**.
- A resposta **201** tem o mesmo envelope da seção 6.1, com `status: "PENDENTE"`,
  `antes: null`, `depois` como objeto (incluindo `cod_loja: null` quando não aplicável),
  autor obtido do token e `decisao: null`. As correções antigas continuam retornando
  `antes` e `depois` escalares.
- Um pedido pendente igual ou um evento já registrado, inclusive por importação,
  retorna **409**, `message: "Intercorrência igual já registrada ou pendente"`.
  A identidade é matrícula, tipo, início, fim e loja; motivo, autor e origem não a alteram.
  Pedidos reprovados permitem nova solicitação.
- A aprovação verifica novamente a existência da matrícula, da loja e de eventos iguais.
  Se a base mudou, retorna **409** e mantém o pedido pendente.
- Apenas a aprovação insere em `evento_rh`, na mesma transação da decisão, com
  `origem: "solicitacao_alteracao:<id>"` para rastrear autor, motivo e aprovador.
  Pendências e reprovações não geram eventos. Os registros importados continuam efetivos.
  `DEMISSAO` registra o evento, sem editar `funcionario.data_demissao`; a correção dessa
  coluna continua sendo `DATA_DEMISSAO`.
- A fila e o detalhe da seção 6.2 incluem esses tipos; a decisão segue a seção 6.3,
  inclusive **403** `Outra pessoa precisa aprovar esta correção` para decisão pelo autor
  e **400** para reprovação sem justificativa.
- O endpoint público aceita `origem: "USUARIO"`. A origem `LANA` só é definida pelo
  fluxo interno autenticado do chat, após confirmação do usuário (integração A6-124).
  Esta tarefa não implementa a conversa nem sua confirmação.

## Persistência e concorrência

As validações e o INSERT de `evento_rh` participam da transação de
`SolicitacaoAlteracaoService`. A decisão trava a solicitação e depois o funcionário;
a criação de RH trava o funcionário antes de consultar duplicatas pendentes/efetivas.
Isso serializa criações e aprovações para a mesma matrícula pela aplicação.
O campo `origem` do evento referencia a solicitação, que mantém os dados da auditoria.

Não se adiciona uma unicidade global ignorando `origem` na tabela `evento_rh`:
a importação existente pode repetir registros de fontes distintas. Duplicidades são
verificadas independentemente da origem na API, novamente ao aprovar. O script de
importação não participa dos locks da aplicação; não há garantia nova de exclusão
mútua entre uma importação externa simultânea e uma decisão.

## Como testar

Com JDK compatível com o projeto, Maven Wrapper e Docker acessível ao usuário:

```bash
bash mvnw -q -Dtest=IntercorrenciaRhApiTest,SolicitacaoAlteracaoServiceTest,SolicitacaoAlteracaoControllerTest test
bash mvnw -q test
```

`IntercorrenciaRhApiTest` sobe PostgreSQL 17 via Testcontainers, executa as migrations
e usa MockMvc com JWT. Não requer banco de desenvolvimento nem chamada à IA.
Os testes abrangem os cinco tipos, validações, autorização, quatro olhos, listagens,
duplicatas pendentes/importadas/aprovadas, reprovação, repetição e concorrência de
criação/decisão. Uma falha de persistência após o INSERT verifica o rollback conjunto.

Validação manual com backend e banco configurados:

1. Autentique um Gerente e dois Supervisores em `/api/auth/login`.
2. Use uma matrícula e, para cobertura, uma loja existentes para enviar o exemplo
   acima. Confira HTTP 201 e o pedido em `/api/correcoes/minhas`; nenhum evento novo
   deve existir ainda em `evento_rh`.
3. Com o token de outro Supervisor, consulte `/api/correcoes/fila` e envie
   `{"decisao":"APROVAR","justificativa":"Conferido com RH"}` para a rota de decisão.
   Confira `APROVADA`, autor/decisor e o evento com `origem` apontando para o pedido.
4. Abra outro pedido como Supervisor. Tente decidi-lo com o mesmo token: deve
   retornar 403 com a mensagem de quatro olhos. Outro Supervisor pode decidir.
5. Reprove um pedido diferente com `{"decisao":"REPROVAR","justificativa":""}`:
   deve retornar 400. Preencha a justificativa e confira `REPROVADA`, sem evento.
6. Reenvie um pedido pendente ou aprovado idêntico: deve retornar 409. Datas inválidas,
   matrícula ausente na base ou cobertura sem loja devem retornar 400.

## Contrato no repositório principal

`contratos-sprint-2-a6-111.patch` atualiza a seção 6 do documento original do
`domrock-Camplana`, incluindo `/minhas` e o contrato estruturado de RH. A cópia local
desse repositório não estava disponível durante a implementação; o patch não foi
aplicado ao remoto. Na branch de documentação do repositório principal, aplicar com:

```bash
git apply --check /caminho/domrock-backend/docs/contratos-sprint-2-a6-111.patch
git apply /caminho/domrock-backend/docs/contratos-sprint-2-a6-111.patch
```

A referência externa aos cenários `docs/backlogs/sprint-3/gherkin.md#us22` não estava
acessível. A cobertura foi baseada nos critérios da A6-111 e no texto da US-22
fornecidos na tarefa. Revisão de PR, merge e aceite do PO ainda dependem da equipe.
