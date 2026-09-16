package br.com.camplana.Repository;

import br.com.camplana.Entity.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface VendaRepository extends JpaRepository<Venda, Integer> {

    boolean existsByDateRefBetween(LocalDate inicio, LocalDate fim);

}