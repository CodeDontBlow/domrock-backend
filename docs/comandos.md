# Guia de Comandos Úteis

Este documento contém os principais comandos utilizados no desenvolvimento e manutenção do backend Java/Spring Boot.

## Execução da Aplicação

```bash
# Iniciar a aplicação em desenvolvimento (com Hot Reload via DevTools)
./mvnw spring-boot:run

# Iniciar em modo Debug (porta padrão 5005 para anexar a IDE)
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

## Build e Empacotamento

```bash
# Limpar artefatos de compilações anteriores
./mvnw clean

# Compilar o projeto e gerar o executável (.jar) sem rodar testes
./mvnw package -DskipTests

# Executar build completo com testes
./mvnw clean package
```

## Testes

```bash
# Executar a suíte de testes unitários e de integração (Testcontainers)
./mvnw test

# Rodar uma classe de teste específica
./mvnw test -Dtest=NomeDaClasseTest
```

## Banco de Dados e Docker Local

```bash
# Subir contêiner do PostgreSQL isolado (caso não use Dev Containers)
docker run --name postgres-camplana -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -e POSTGRES_DB=postgres -p 5432:5432 -d postgres

# Parar e remover o contêiner local do banco
docker stop postgres-camplana && docker rm postgres-camplana
```