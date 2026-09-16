package br.com.camplana.Repository;

import br.com.camplana.Entity.Loja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LojaRepository extends JpaRepository<Loja, Integer> {
    Optional<Loja> findByCodLoja(String codLoja);
}