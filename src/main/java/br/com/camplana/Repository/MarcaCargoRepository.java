package br.com.camplana.Repository;

import br.com.camplana.Entity.MarcaCargo;
import br.com.camplana.Entity.MarcaCargoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaCargoRepository extends JpaRepository<MarcaCargo, MarcaCargoId> {
}