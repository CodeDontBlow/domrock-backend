# Backend - CamplanaAI

API desenvolvida em Java com Spring Boot para o projeto CamplanaAI, focada no gerenciamento de regras de negócio com suporte a IA Generativa. Utiliza arquitetura ... PostgreSQL e Dev Containers para automação e padronização do ambiente.

## Tecnologias Utilizadas

![Java](https://img.shields.io/badge/Java-17-141416?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/Spring_Boot-141416?style=for-the-badge&logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-141416?style=for-the-badge&logo=apachemaven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-141416?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-141416?style=for-the-badge&logo=docker&logoColor=white)

---
## Estrutura do Projeto

```text
domrock-backend/
│
├── .devcontainer/         # Configuração do ambiente isolado (VS Code + Docker Compose)
├── .mvn/                  # Wrapper do Maven
├── docs/                  # Documentação técnica do projeto
│   └── comandos.md        # Guia rápido de comandos Maven e Docker
│
├── src/
│   ├── main/
│   │   ├── java/br/com/camplana/
│   │   │   ├── config/    # Beans globais e configurações de segurança/CORS
│   │   │   ├── Controller/# Controllers REST (Endpoints expostos ao Vue.js)
│   │   │   ├── Entity/      # Entidades JPA (PostgreSQL)
│   │   │   ├── Repository/  # Interfaces Spring Data JPA
│   │   │   └── Service/   # Lógica de negócio e integração com a IA
│   │   │
│   │   └── resources/
│   │       └── application.properties # Configurações da aplicação
│   │
│   └── test/              # Testes unitários e de integração (Testcontainers)
│
├── Dockerfile             # Imagem do container de desenvolvimento
├── mvnw / mvnw.cmd        # Executáveis do Maven Wrapper
├── pom.xml                # Mapeamento de dependências do Maven
├── CHANGELOG.md           # Histórico de alterações do projeto
├── LICENSE                # Licença do projeto
└── README.md              # Documentação principal
```

**Tipo de Arquitetura:** Layered Architecture (Arquitetura em Camadas REST)[cite: 1]

**Guia de Comandos:** Veja [docs/comandos.md](docs/comandos.md)

### Banco de Dados

- **Banco relacional principal**: PostgreSQL 17
- **Migrations**: ...

## Documentação Complementar

- [docs/comandos.md](docs/comandos.md)

## Licença

Este projeto está sob a licença especificada no arquivo [LICENSE](LICENSE).
