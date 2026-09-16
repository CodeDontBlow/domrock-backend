package br.com.camplana.Repository;

import br.com.camplana.Entity.FuncionarioLoja;
import br.com.camplana.Entity.FuncionarioLojaId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface FuncionarioLojaRepository extends JpaRepository<FuncionarioLoja, FuncionarioLojaId> {

    boolean existsByIdDateRef(LocalDate dateRef);

}