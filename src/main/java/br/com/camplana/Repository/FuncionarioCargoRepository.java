package br.com.camplana.Repository;

import br.com.camplana.Entity.FuncionarioCargo;
import br.com.camplana.Entity.FuncionarioCargoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncionarioCargoRepository extends JpaRepository<FuncionarioCargo, FuncionarioCargoId> {
}