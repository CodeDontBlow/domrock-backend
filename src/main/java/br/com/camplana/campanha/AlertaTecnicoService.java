package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.AlertaTecnicoResponse;
import br.com.camplana.dto.campanha.CriarAlertaRequest;
import br.com.camplana.dto.campanha.ResolverAlertaRequest;
import br.com.camplana.entity.AlertaTecnico;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.OrigemAlerta;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.StatusAlerta;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.AlertaTecnicoRepository;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AlertaTecnicoService {

    private final AlertaTecnicoRepository alertaRepository;
    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository versaoRepository;
    private final ObjectMapper objectMapper;
    private final Clock relogio;

    public AlertaTecnicoService(AlertaTecnicoRepository alertaRepository,
                                CampanhaRepository campanhaRepository,
                                CampanhaVersaoRepository versaoRepository,
                                ObjectMapper objectMapper,
                                Clock relogio) {
        this.alertaRepository = alertaRepository;
        this.campanhaRepository = campanhaRepository;
        this.versaoRepository = versaoRepository;
        this.objectMapper = objectMapper;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<AlertaTecnicoResponse> listar(long campanhaId, int numeroVersao, Usuario usuario) {
        if (usuario.getPerfil() != Perfil.SUPERVISOR && usuario.getPerfil() != Perfil.AUDITOR) {
            throw new ForbiddenException("Seu perfil não permite esta ação.");
        }
        validarCampanhaEVersao(campanhaId, numeroVersao);
        return alertaRepository.findByCampanhaIdAndNumeroVersaoOrderByIdAsc(campanhaId, numeroVersao)
                .stream()
                .map(AlertaTecnicoResponse::from)
                .toList();
    }

    @Transactional
    public AlertaTecnicoResponse abrir(long campanhaId, int numeroVersao, CriarAlertaRequest pedido, Usuario auditor) {
        if (auditor.getPerfil() != Perfil.AUDITOR) {
            throw new ForbiddenException("Apenas o Auditor pode abrir alertas técnicos.");
        }
        if (pedido == null || pedido.descricao() == null || pedido.descricao().isBlank()) {
            throw new BadRequestException("Descrição obrigatória");
        }
        Campanha campanha = validarCampanhaEVersao(campanhaId, numeroVersao);
        String tipo = (pedido.tipo() != null && !pedido.tipo().isBlank()) ? pedido.tipo().strip() : "MANUAL";
        AlertaTecnico alerta = new AlertaTecnico(
                campanha,
                numeroVersao,
                OrigemAlerta.MANUAL,
                tipo,
                pedido.descricao().strip(),
                StatusAlerta.ABERTO,
                auditor,
                Instant.now(relogio),
                null,
                null,
                null
        );
        return AlertaTecnicoResponse.from(alertaRepository.save(alerta));
    }

    @Transactional
    public AlertaTecnicoResponse resolver(long campanhaId, int numeroVersao, long alertaId,
                                         ResolverAlertaRequest pedido, Usuario auditor) {
        if (auditor.getPerfil() != Perfil.AUDITOR) {
            throw new ForbiddenException("Apenas o Auditor pode resolver alertas técnicos.");
        }
        if (pedido == null || pedido.justificativa() == null || pedido.justificativa().isBlank()) {
            throw new BadRequestException("Informe a justificativa para resolver o alerta.");
        }
        validarCampanhaEVersao(campanhaId, numeroVersao);
        AlertaTecnico alerta = alertaRepository.findById(alertaId)
                .orElseThrow(() -> new NotFoundException("Alerta " + alertaId + " não encontrado"));
        if (!alerta.getCampanha().getId().equals(campanhaId) || alerta.getNumeroVersao() != numeroVersao) {
            throw new NotFoundException("Alerta " + alertaId + " não pertence à versão " + numeroVersao + " da campanha " + campanhaId);
        }
        alerta.setStatus(StatusAlerta.RESOLVIDO);
        alerta.setResolvidoPor(auditor);
        alerta.setResolvidoEm(Instant.now(relogio));
        alerta.setJustificativa(pedido.justificativa().strip());
        return AlertaTecnicoResponse.from(alertaRepository.save(alerta));
    }

    @Transactional
    public List<AlertaTecnico> gravarAlertasAutomaticos(Campanha campanha, int numeroVersao, String alertasJson) {
        if (alertasJson == null || alertasJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(alertasJson);
            return gravarAlertasAutomaticos(campanha, numeroVersao, node);
        } catch (Exception e) {
            // texto plano que não é JSON: grava como um alerta único
            AlertaTecnico alerta = new AlertaTecnico(
                    campanha, numeroVersao, OrigemAlerta.AUTOMATICO,
                    "CONSULTA_BLOQUEADA", alertasJson.strip(),
                    StatusAlerta.ABERTO, null, Instant.now(relogio), null, null, null
            );
            return List.of(alertaRepository.save(alerta));
        }
    }

    @Transactional
    public List<AlertaTecnico> gravarAlertasAutomaticos(Campanha campanha, int numeroVersao, JsonNode conteudo) {
        if (conteudo == null || conteudo.isMissingNode() || conteudo.isNull()) {
            return List.of();
        }
        JsonNode alertasNode = conteudo;
        if (conteudo.isTextual()) {
            return gravarAlertasAutomaticos(campanha, numeroVersao, conteudo.asText());
        }
        if (alertasNode.has("alertas")) {
            alertasNode = alertasNode.get("alertas");
        } else if (alertasNode.has("alertas_json")) {
            JsonNode aj = alertasNode.get("alertas_json");
            if (aj.isTextual()) {
                return gravarAlertasAutomaticos(campanha, numeroVersao, aj.asText());
            } else {
                alertasNode = aj;
            }
        } else if (alertasNode.has("alertas_tecnicos")) {
            alertasNode = alertasNode.get("alertas_tecnicos");
        }

        List<AlertaTecnico> criados = new ArrayList<>();
        if (alertasNode.isArray()) {
            for (JsonNode item : alertasNode) {
                AlertaTecnico alerta = converterItem(campanha, numeroVersao, item);
                if (alerta != null) {
                    criados.add(alertaRepository.save(alerta));
                }
            }
        } else if (alertasNode.isObject() && (alertasNode.has("tipo") || alertasNode.has("descricao"))) {
            AlertaTecnico alerta = converterItem(campanha, numeroVersao, alertasNode);
            if (alerta != null) {
                criados.add(alertaRepository.save(alerta));
            }
        }
        return criados;
    }

    @Transactional(readOnly = true)
    public boolean temAlertaAberto(long campanhaId, int numeroVersao) {
        return alertaRepository.existsByCampanhaIdAndNumeroVersaoAndStatus(campanhaId, numeroVersao, StatusAlerta.ABERTO);
    }

    private Campanha validarCampanhaEVersao(long campanhaId, int numeroVersao) {
        Campanha campanha = campanhaRepository.findById(campanhaId)
                .orElseThrow(() -> new NotFoundException("Campanha " + campanhaId + " não encontrada"));
        versaoRepository.findByCampanhaIdAndNumero(campanhaId, numeroVersao)
                .orElseThrow(() -> new NotFoundException("Versão " + numeroVersao + " da campanha " + campanhaId + " não encontrada"));
        return campanha;
    }

    private AlertaTecnico converterItem(Campanha campanha, int numeroVersao, JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        String tipo = "CONSULTA_BLOQUEADA";
        String descricao;
        if (item.isObject()) {
            tipo = item.hasNonNull("tipo") ? item.get("tipo").asText()
                    : item.hasNonNull("type") ? item.get("type").asText() : "CONSULTA_BLOQUEADA";
            descricao = item.hasNonNull("descricao") ? item.get("descricao").asText()
                    : item.hasNonNull("mensagem") ? item.get("mensagem").asText()
                    : item.hasNonNull("motivo") ? item.get("motivo").asText()
                    : item.toString();
        } else if (item.isTextual()) {
            descricao = item.asText();
            String lower = descricao.toLowerCase(Locale.ROOT);
            if (lower.contains("código") || lower.contains("codigo") || lower.contains("python")) {
                tipo = "CODIGO_SUSPEITO";
            }
        } else {
            descricao = item.toString();
        }
        return new AlertaTecnico(
                campanha,
                numeroVersao,
                OrigemAlerta.AUTOMATICO,
                tipo,
                descricao,
                StatusAlerta.ABERTO,
                null,
                Instant.now(relogio),
                null,
                null,
                null
        );
    }
}
