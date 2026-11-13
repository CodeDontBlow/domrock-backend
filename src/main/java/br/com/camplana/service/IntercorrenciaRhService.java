package br.com.camplana.service;

import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.SolicitacaoAlteracao;
import br.com.camplana.entity.TipoAlteracao;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.repository.EventoRhRepository;
import br.com.camplana.repository.LojaRepository;
import br.com.camplana.repository.SolicitacaoAlteracaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/** Valida e aplica eventos dentro da transação da solicitação, sob o lock do funcionário. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class IntercorrenciaRhService {

    private static final String DUPLICADA = "Intercorrência igual já registrada ou pendente";
    private static final Set<String> CAMPOS = Set.of("data_inicio", "data_fim", "cod_loja");
    private final EventoRhRepository eventoRepository;
    private final SolicitacaoAlteracaoRepository solicitacaoRepository;
    private final LojaRepository lojaRepository;
    private final ObjectMapper objectMapper;

    private record Dados(LocalDate inicio, LocalDate fim, String loja) {}

    public String validarCriacao(SolicitacaoRequest dto, String matricula) {
        if (dto.motivo() == null || dto.motivo().isBlank()) {
            throw new BadRequestException("Motivo é obrigatório para intercorrências de RH");
        }
        if (dto.antes() != null && !dto.antes().isNull()) {
            throw new BadRequestException("O valor 'antes' deve ser null para uma nova intercorrência");
        }
        Dados dados = lerDados(dto.tipo(), dto.depois());
        if (dados.loja() != null && lojaRepository.findByCodLoja(dados.loja()).isEmpty()) {
            throw new BadRequestException("Loja não encontrada: " + dados.loja());
        }
        String normalizado = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("data_inicio", dados.inicio().toString())
                .put("data_fim", dados.fim().toString())
                .put("cod_loja", dados.loja()));
        if (solicitacaoRepository.existsIntercorrenciaPendente(matricula, dto.tipo().name(), normalizado)) {
            throw new ConflictException(DUPLICADA);
        }
        verificarEvento(matricula, dto.tipo(), dados);
        return normalizado;
    }

    public void aplicar(SolicitacaoAlteracao solicitacao) {
        Dados dados = lerDados(solicitacao.getTipo(), objectMapper.readTree(solicitacao.getValorDepois()));
        if (dados.loja() != null && lojaRepository.findByCodLoja(dados.loja()).isEmpty()) {
            throw new ConflictException("A base mudou desde o pedido: loja não encontrada");
        }
        // Um evento importado depois da abertura também impede a aprovação de uma duplicata.
        verificarEvento(solicitacao.getMatricula(), solicitacao.getTipo(), dados);
        eventoRepository.inserir(solicitacao.getId(), solicitacao.getMatricula(),
                solicitacao.getTipo().name().toLowerCase(Locale.ROOT), dados.inicio(), dados.fim(), dados.loja());
    }

    private void verificarEvento(String matricula, TipoAlteracao tipo, Dados dados) {
        if (eventoRepository.existe(matricula, tipo.name().toLowerCase(Locale.ROOT),
                dados.inicio(), dados.fim(), dados.loja())) {
            throw new ConflictException(DUPLICADA);
        }
    }

    private Dados lerDados(TipoAlteracao tipo, JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new BadRequestException("O valor 'depois' deve ser um objeto com data_inicio, data_fim e cod_loja");
        }
        if (!CAMPOS.containsAll(node.propertyNames())) {
            throw new BadRequestException("Campo desconhecido em 'depois' da intercorrência");
        }
        LocalDate inicio = data(node, "data_inicio");
        LocalDate fim = data(node, "data_fim");
        if (fim.isBefore(inicio)) {
            throw new BadRequestException("Data final deve ser igual ou posterior à data inicial");
        }
        String loja = texto(node, "cod_loja");
        if (tipo == TipoAlteracao.COBERTURA_LOJA && loja == null) {
            throw new BadRequestException("Loja é obrigatória para cobertura_loja");
        }
        if (tipo != TipoAlteracao.COBERTURA_LOJA && loja != null) {
            throw new BadRequestException("cod_loja só se aplica a cobertura_loja");
        }
        return new Dados(inicio, fim, loja);
    }

    private LocalDate data(JsonNode node, String campo) {
        String valor = texto(node, campo);
        if (valor == null) {
            throw new BadRequestException(campo + " é obrigatória");
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Data inválida em '" + campo + "'. Use YYYY-MM-DD.");
        }
    }

    private String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isTextual()) {
            throw new BadRequestException(campo + " deve ser um texto");
        }
        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }
}
