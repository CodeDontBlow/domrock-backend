package br.com.camplana.Repository;

import br.com.camplana.Entity.Funcionario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Integer> {
    Optional<Funcionario> findByMatricula(String matricula);
}