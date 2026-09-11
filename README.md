# Backend - CamplanaAI

API desenvolvida em Java com Spring Boot para o projeto CamplanaAI, utilizando arquitetura xyz para gerenciamento de banco de dados e Docker para containerização.

## Tecnologias Utilizadas

[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)

[![Docker](https://img.shields.io/badge/Docker-2CA5E0?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

## Pré-requisitos

- **[Visual Studio Code](https://code.visualstudio.com/)** - Editor de código
- **[PostgreSQL](https://www.postgresql.org)**
- **[Docker](https://www.docker.com/)** - (Opcional)

## Como Executar

1. **Clone o repositório**

   ```bash
   git clone https://github.com/CodeDontBlow/domrock-backend.git
   cd domrock-backend
   ```

É possível rodar o backend de duas formas principais:

### Opção 1: PostgreSQL via Docker

...

### Opção 2: PostgreSQL Local + Backend Local

...

5. **Acessar a documentação da API**: http://localhost:3333/api

## Estrutura do Projeto

```text
domrock-backend/
│
├── .devcontainer/         # Configuração Dev Containers
├── .mvn/                  # Configuração Maven
├── docs/                  # Documentação
│   ├── comandos.md           # Comandos e guias detalhados
│   └── security.md           # Guias de segurança
│
├── src/                   # Código fonte
│   ├── main.ts               # Entry point e configuração global
│   ├── app.module.ts         # Módulo raiz (agregação de módulos)
│   ├── common/            # Código compartilhado
│   │   ├── decorators/       # Decoradores customizados
│   │   ├── dtos/             # DTOs globais
│   │   ├── filters/          # Filtros de exceção
│   │   └── interceptors/     # Interceptadores
│   ├── database/          # Camada de banco de dados
│   │   └── prisma/           # Módulo Prisma
│   └── 📂 modules/           # Módulos de negócio
│       ├── 📂 auth/          # Autenticação e autorização
│       ├── 📂 user/          # Gerenciamento de usuários
│       ├── 📂 agent/         # Gerenciamento de agentes/atendentes
│       ├── 📂 company/       # Gerenciamento de empresas
│       ├── 📂 ticket/        # Gerenciamento de tickets
│       ├── 📂 chat/          # Chat em tempo real (WebSocket)
│       ├── 📂 support-group/ # Grupos de suporte
│       ├── 📂 ticket-subject/# Assuntos de tickets
│       ├── 📂 triage-rule/   # Regras de triagem automatizada
│       └── 📂 accessCode/    # Códigos de acesso
│
├── test/                     # Testes E2E
├── compose.yaml              # Orquestração de containers
├── Dockerfile                # Imagem Docker
├── .gitignore                # Arquivos a serem ignorados
├── .gitattributes            # Imagem Docker
├── mvnw                      # Imagem Docker
├── mvnw.cmd                  # Imagem Docker
├── pom.xml                   # Imagem Docker
├── CHANGELOG                 # Versões do sistema com principais alterações e registro de mudanças
├── LICENSE                   # Licença do projeto
└── README.md                 # Você está aqui
```

> **Tipo de Arquitetura:** Modular

> **Comandos completos:** Veja [docs/comandos.md](docs/comandos.md)

## Arquitetura

### Banco de Dados

- **Banco relacional principal**: PostgreSQL
- **Migrations**: Prisma (`prisma/migrations`)

## Comandos Úteis

```bash
# Desenvolvimento
./mvnw spring-boot:run       # Inicia com hot-reload
./mvnw spring-boot:run     # Inicia em modo debug

# Produção
npm run build              # Build do projeto
npm run start:prod         # Inicia versão otimizada

# Banco de Dados
npx prisma generate        # Gerar cliente Prisma
npx prisma studio          # UI visual do banco
npx prisma migrate dev --name nome_da_migration  # Criar nova migration
npx prisma migrate deploy  # Aplicar migrations
npx prisma db seed         # Popular dados iniciais

# Linting e Testes
npm run lint               # ESLint
npm run test               # Testes unitários
npm run test:e2e           # Testes E2E
Copy-Item .env.example .env       # Cobertura de testes
```

## Documentação Complementar

- [docs/comandos.md](docs/comandos.md)
- [docs/security.md](docs/security.md)

## Licença

Este projeto está sob a licença especificada no arquivo [LICENSE](LICENSE).
