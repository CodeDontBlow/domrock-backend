package br.com.camplana.Repository;

import br.com.camplana.Entity.Cargo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CargoRepository extends JpaRepository<Cargo, Integer> {
    Optional<Cargo> findByCodCargo(String codCargo);
}