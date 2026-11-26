package br.com.camplana.controller;

import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse;
import br.com.camplana.dto.correcao.SolicitacaoDoChatRequest;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.Usuario;
import br.com.camplana.service.ChatService;
import br.com.camplana.service.SolicitacaoAlteracaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de confirmação de correções e intercorrências abertas pelo chat (A6-81).
 * POST /api/chat/{chat_id}/correcoes
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatCorrecaoController {

    private final ChatService chatService;
    private final SolicitacaoAlteracaoService solicitacaoAlteracaoService;

    @PostMapping("/{chat_id}/correcoes")
    public ResponseEntity<SolicitacaoDetalheResponse> criarCorrecao(
            @PathVariable("chat_id") String chatId,
            @Valid @RequestBody SolicitacaoDoChatRequest body,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        chatService.validarPosseConversa(chatId, usuarioLogado);
        SolicitacaoRequest request = body.paraSolicitacaoRequest(chatId);
        SolicitacaoDetalheResponse response = solicitacaoAlteracaoService.criarSolicitacaoDaLana(request, usuarioLogado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
