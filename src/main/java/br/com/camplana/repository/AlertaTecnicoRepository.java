package br.com.camplana.repository;

import br.com.camplana.entity.AlertaTecnico;
import br.com.camplana.entity.StatusAlerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertaTecnicoRepository extends JpaRepository<AlertaTecnico, Long> {

    List<AlertaTecnico> findByCampanhaIdAndNumeroVersaoOrderByIdAsc(Long campanhaId, int numeroVersao);

    List<AlertaTecnico> findByCampanhaIdAndNumeroVersaoAndStatusOrderByIdAsc(Long campanhaId, int numeroVersao, StatusAlerta status);

    boolean existsByCampanhaIdAndNumeroVersaoAndStatus(Long campanhaId, int numeroVersao, StatusAlerta status);

    Optional<AlertaTecnico> findByIdAndCampanhaIdAndNumeroVersao(Long id, Long campanhaId, int numeroVersao);
}
