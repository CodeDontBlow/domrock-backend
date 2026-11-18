# A6-103 — Endpoints da Auditoria (visão de negócio)

## Dependência incorporada

Esta implementação incorpora como alterações locais o conteúdo da A6-102 do
[PR #8](https://github.com/CodeDontBlow/domrock-backend/pull/8), revisão
`0116c81b3793d0776c3bc31039fc10a604d9a289`, e acrescenta os contratos necessários
à tela A6-103. Não foi feito merge, commit, push ou troca de branch.

A dependência introduz `Perfil.AUDITOR`, leitura de campanhas/versões/conversas/
correções de outros usuários, restrição de escrita e a migration
`V12__add_perfil_auditor_e_seed_paulo.sql`. O seed disponibiliza o usuário de teste
`paulo@camplana.com.br` com a senha de desenvolvimento usada pelos demais seeds.
Essa mesma migration deve permanecer única ao integrar a A6-102 posteriormente.

## Consultas

Todas exigem Bearer token. O Auditor recebe 403 para operações de escrita,
importação, administração de usuários e filas de aprovação.

| Método e rota | Perfil | Resposta |
|---|---|---|
| `GET /api/campanhas/auditoria` | AUDITOR | Todas as campanhas, incluindo rascunhos e arquivadas, por última alteração decrescente |
| `GET /api/correcoes/auditoria` | AUDITOR | Todas as solicitações, de todos os autores/status, por criação e ID decrescentes |
| `GET /api/campanhas/{id}` | AUDITOR pode consultar qualquer autor | Detalhe com `acoes: []` |
| `GET /api/campanhas/{id}/versoes/{numero}` | AUDITOR pode consultar qualquer autor | Versão com `acoes: []` |
| `GET /api/chat/{chat_id}/mensagens` | AUDITOR pode consultar qualquer autor | Conversa e trilha gravadas |
| `GET /api/correcoes/{id}` | AUDITOR pode consultar qualquer autor | Detalhe da solicitação e decisão |
| `GET /api/simulacoes/{id}` | AUDITOR pode consultar qualquer autor | Resultado já gravado; não inicia nova simulação |

O filtro opcional `status` da lista de campanhas aceita os cinco valores existentes:
`RASCUNHO`, `AGUARDANDO_APROVACAO`, `APROVADA`, `REPROVADA`, `ARQUIVADA`.
Status inválido retorna 400. Sem filtro, nenhum status é excluído. A busca por nome
na tela é local. Listas vazias retornam `200 []`; sessão ausente retorna 401;
Gerente, Supervisor e Admin recebem 403 nas duas listagens globais de auditoria.
As permissões anteriores desses perfis nas demais rotas são preservadas.

### Autor no resumo das campanhas

`CampanhaResumoResponse` passa a incluir `autor: { id, nome }`. É um campo adicional,
também presente em `/api/campanhas/minhas`, sem remover ou renomear campos existentes.
O autor vem da campanha persistida, não do usuário que está consultando.

```json
{
  "id": 12,
  "nome": "Campanha de novembro",
  "autor": { "id": 1, "nome": "Gabriela" },
  "status": "RASCUNHO",
  "vigencia": null,
  "em_simulacao": false,
  "versao_atual": null,
  "periodo": { "data_inicio": "2026-11-20", "data_fim": "2026-11-30" },
  "custo_adicional": 1000.00,
  "atualizada_em": "2026-11-17T20:00:00Z"
}
```

### Lista de correções

Reutiliza `SolicitacaoDetalheResponse`, incluindo `antes`, `depois`, `motivo`, `autor`,
`criada_em`, `status` e `decisao` (`por`, `em`, `justificativa`). Pedidos pendentes têm
`decisao: null`; aprovados e reprovados conservam sua auditoria. Inclui pedidos que o
próprio Auditor tenha aberto quando possuía outro perfil. A consulta não muda status,
não aplica correções e não insere eventos de RH.

O backend nesta branch contém os tipos de correção da A6-80. A integração da A6-111,
com os novos tipos de RH, permanece uma tarefa separada: esta alteração não reaplica
a implementação anterior de RH nem altera seus futuros contratos.

## Complementos sobre a A6-102

- Controller, service e repository expõem a listagem global de correções; a regra
  exclusiva de AUDITOR é avaliada antes do padrão geral de leitura de `/api/correcoes/**`.
- O service também verifica o perfil ao listar correções, e usa EntityGraph para
  carregar autores e decisores na consulta.
- O resumo de campanhas inclui o autor para evitar uma consulta adicional por linha no frontend.
- Mesmo uma campanha de autoria do Auditor retorna ações vazias se seu perfil mudou
  depois da criação. A API continua recusando alterações.
- A leitura de simulações pelo Auditor, prevista no service da A6-102, também é
  liberada no filtro HTTP. POST continua restrito a Gerente/Supervisor.
- Frontend: contrato consumido e manual atualizados em `docs/auditoria.md`.

## Testes

Na raiz do backend, com Docker disponível ao usuário:

```bash
bash mvnw -q -Dtest=AuditoriaApiTest,CampanhaApiTest,ChatApiTest,SecurityPorPerfilTest,SolicitacaoAlteracaoControllerTest,AuthControllerTest test
bash mvnw -q test
```

`AuditoriaApiTest` usa PostgreSQL via Testcontainers, JWT e os controllers reais.
Cobre campanhas de autores distintos nos cinco status, campo autor, filtro por
status, listas vazias, dados e ordem das correções, restrições por perfil, leitura
sem efeitos de escrita, mudança de perfil do autor e leitura de simulações alheias.
Os testes incorporados da A6-102 cobrem login, conversa, versão e bloqueios de escrita.

Validação manual: suba o backend com as migrations aplicadas e o frontend A6-103,
faça login com o Auditor de teste e consulte a tela Auditoria. Cadastre dados de
exemplo usando os perfis adequados; o Auditor não cria dados. Confira na aba Network
as consultas GET e os campos acima. Revisão, merge e aceite do PO continuam a cargo
da equipe. O repositório principal `domrock-Camplana` não foi alterado.
