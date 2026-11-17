package br.com.camplana.security;

import br.com.camplana.entity.Perfil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Filtro único de visibilidade de SQL e código gerado (A6-106 / US-20).
 * Apenas SUPERVISOR e AUDITOR recebem os campos técnicos {@code sql}, {@code sql_executado}, {@code codigo} e {@code codigo_gerado}.
 */
@Component
public class FiltroConteudoTecnico {

    private static final Set<String> CHAVES_TECNICAS = Set.of("sql", "sql_executado", "codigo", "codigo_gerado");

    private final ObjectMapper objectMapper;

    public FiltroConteudoTecnico(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean podeVerCodigoGerado(Perfil perfil) {
        return perfil != null && (perfil == Perfil.SUPERVISOR || "AUDITOR".equals(perfil.name()));
    }

    public JsonNode filtrarJsonNode(JsonNode node, Perfil perfil) {
        if (podeVerCodigoGerado(perfil) || node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        JsonNode copia = node.deepCopy();
        limparRecursivamente(copia);
        return copia;
    }

    public String filtrarJsonString(String jsonRaw, Perfil perfil) {
        if (podeVerCodigoGerado(perfil) || jsonRaw == null || jsonRaw.isBlank()) {
            return jsonRaw;
        }
        JsonNode arvore = objectMapper.readTree(jsonRaw);
        JsonNode filtrado = filtrarJsonNode(arvore, perfil);
        return objectMapper.writeValueAsString(filtrado);
    }

    private void limparRecursivamente(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.remove(CHAVES_TECNICAS);
            for (JsonNode filho : obj) {
                limparRecursivamente(filho);
            }
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode item : arr) {
                limparRecursivamente(item);
            }
        }
    }
}
