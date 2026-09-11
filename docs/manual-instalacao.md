# Como Executar

## Pré-requisitos
- **[Java 17](https://www.oracle.com/java/)** instalado e configurado nas variáveis de ambiente (JAVA_HOME).

### Para Opção 1: Dev Container (Recomendado)
- **[Docker Desktop](https://www.docker.com/)** instalado e aberto (Engine running).

- **[Visual Studio Code](https://code.visualstudio.com/)** com a extensão Dev Containers instalada.

### Para Opção 2: Execução Local (Sem Dev Container)
- **[Docker](https://www.docker.com/)** (para subir a imagem do PostgreSQL) ou uma instância local do PostgreSQL rodando.

- **IDE** IDE de sua preferência ([Visual Studio Code](https://code.visualstudio.com/) ou ou **[IntelliJ IDEA](https://www.jetbrains.com/pt-br/idea/)**).

## Passo a passo:
1. **Clone o repositório**:
```bash
git clone https://github.com/CodeDontBlow/domrock-backend.git
cd domrock-backend

```
## Opção 1: Via Dev Container (Recomendado)

2. Abra a pasta domrock-backend no VS Code.

3. Pressione F1 (ou Ctrl+Shift+P), digite e selecione: Dev Containers: Reopen in Container.

4. Aguarde o build automático do ambiente isolado.

5. Abra o terminal integrado do VS Code (Ctrl + ') e execute:

```bash
./mvnw spring-boot:run
```

## Opção 2: Execução Local (Sem Dev Container)

2. Suba o banco PostgreSQL via Docker:
```bash
docker run --name postgres-camplana -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -e POSTGRES_DB=postgres -p 5432:5432 -d postgres
```

3. Execute a aplicação via Maven Wrapper:

```bash
./mvnw spring-boot:run
```

## Endpoints de Verificação
Após subir a aplicação por qualquer uma das opções, você poderá acessar:

> API Base: http://localhost:8080

> Spring Boot Actuator: http://localhost:8080/actuator
