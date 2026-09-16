package br.com.camplana.Repository;

import br.com.camplana.Entity.FuncionarioLoja;
import br.com.camplana.Entity.FuncionarioLojaId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncionarioLojaRepository extends JpaRepository<FuncionarioLoja, FuncionarioLojaId> {
}