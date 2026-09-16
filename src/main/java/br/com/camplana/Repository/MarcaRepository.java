package br.com.camplana.Repository;

import br.com.camplana.Entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {
    Optional<Marca> findByCodMarca(String codMarca);
}