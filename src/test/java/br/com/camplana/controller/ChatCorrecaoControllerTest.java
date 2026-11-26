package br.com.camplana.controller;

import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse.UsuarioResumo;
import br.com.camplana.dto.correcao.SolicitacaoDoChatRequest;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.OrigemSolicitacao;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.StatusSolicitacao;
import br.com.camplana.entity.TipoAlteracao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.handler.GlobalExceptionHandler;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import br.com.camplana.security.PasswordConfig;
import br.com.camplana.security.SecurityConfig;
import br.com.camplana.security.UsuarioDetailsService;
import br.com.camplana.service.ChatService;
import br.com.camplana.service.SolicitacaoAlteracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ChatCorrecaoController.class,
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class, GlobalExceptionHandler.class})
class ChatCorrecaoControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private ChatService chatService;
    @MockitoBean private SolicitacaoAlteracaoService solicitacaoAlteracaoService;
    @MockitoBean private UsuarioRepository usuarioRepository;

    private Usuario gabriela;
    private Usuario sergio;
    private Usuario paulo;

    @BeforeEach
    void setUp() {
        gabriela = usuario(1, "Gabriela", "gabriela@camplana.com.br", Perfil.GERENTE);
        sergio = usuario(2, "Sérgio", "sergio@camplana.com.br", Perfil.SUPERVISOR);
        paulo = usuario(5, "Paulo", "paulo@camplana.com.br", Perfil.AUDITOR);
    }

    @Test
    void postCorrecoesCriaComOrigemLanaChatIdDoCaminhoEAutorDoTokenRetornando201() throws Exception {
        SolicitacaoDetalheResponse criada = new SolicitacaoDetalheResponse(
                10,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                "33",
                "8",
                "Transferida para a loja 8 em outubro",
                OrigemSolicitacao.LANA,
                "chat-01",
                StatusSolicitacao.PENDENTE,
                new UsuarioResumo(1, "Gabriela"),
                LocalDateTime.of(2026, 11, 26, 10, 0, 0),
                null,
                "aberta pela Lana a pedido de Gabriela"
        );

        when(solicitacaoAlteracaoService.criarSolicitacaoDaLana(any(SolicitacaoRequest.class), any(Usuario.class)))
                .thenReturn(criada);

        String payload = """
                {
                  "tipo": "TRANSFERENCIA_LOJA",
                  "matricula": "MATRIC-38",
                  "competencia": "2025-10",
                  "antes": "33",
                  "depois": "8",
                  "motivo": "Transferida para a loja 8 em outubro"
                }
                """;

        mockMvc.perform(post("/api/chat/chat-01/correcoes")
                        .header("Authorization", bearer(gabriela))
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.origem").value("LANA"))
                .andExpect(jsonPath("$.chat_id").value("chat-01"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.autor.id").value(1))
                .andExpect(jsonPath("$.descricao_origem").value("aberta pela Lana a pedido de Gabriela"));

        verify(chatService).validarPosseConversa(eq("chat-01"), eq(gabriela));

        ArgumentCaptor<SolicitacaoRequest> captor = ArgumentCaptor.forClass(SolicitacaoRequest.class);
        verify(solicitacaoAlteracaoService).criarSolicitacaoDaLana(captor.capture(), eq(gabriela));
        SolicitacaoRequest reqEnviada = captor.getValue();
        assertThat(reqEnviada.chatId()).isEqualTo("chat-01");
        assertThat(reqEnviada.origem()).isEqualTo(OrigemSolicitacao.LANA);
        assertThat(reqEnviada.matricula()).isEqualTo("MATRIC-38");
    }

    @Test
    void postCorrecoesIgnoraOrigemEOutrosCamposNoCorpo() throws Exception {
        SolicitacaoDetalheResponse criada = new SolicitacaoDetalheResponse(
                11,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                "33",
                "8",
                "Transferida",
                OrigemSolicitacao.LANA,
                "chat-01",
                StatusSolicitacao.PENDENTE,
                new UsuarioResumo(1, "Gabriela"),
                LocalDateTime.now(),
                null,
                "aberta pela Lana a pedido de Gabriela"
        );

        when(solicitacaoAlteracaoService.criarSolicitacaoDaLana(any(SolicitacaoRequest.class), any(Usuario.class)))
                .thenReturn(criada);

        String payload = """
                {
                  "origem": "USUARIO",
                  "chat_id": "chat-forjado",
                  "autor": "Invasor",
                  "tipo": "TRANSFERENCIA_LOJA",
                  "matricula": "MATRIC-38",
                  "competencia": "2025-10",
                  "antes": "33",
                  "depois": "8",
                  "motivo": "Transferida"
                }
                """;

        mockMvc.perform(post("/api/chat/chat-01/correcoes")
                        .header("Authorization", bearer(gabriela))
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        ArgumentCaptor<SolicitacaoRequest> captor = ArgumentCaptor.forClass(SolicitacaoRequest.class);
        verify(solicitacaoAlteracaoService).criarSolicitacaoDaLana(captor.capture(), eq(gabriela));
        SolicitacaoRequest reqEnviada = captor.getValue();
        assertThat(reqEnviada.chatId()).isEqualTo("chat-01");
        assertThat(reqEnviada.origem()).isEqualTo(OrigemSolicitacao.LANA);
        assertThat(reqEnviada.autor()).isNull();
    }

    @Test
    void postCorrecoesEmConversaDeOutraPessoaRetorna403() throws Exception {
        doThrow(new ForbiddenException("A conversa chat-01 pertence a outro usuário"))
                .when(chatService).validarPosseConversa(eq("chat-01"), any(Usuario.class));

        String payload = """
                {
                  "tipo": "TRANSFERENCIA_LOJA",
                  "matricula": "MATRIC-38",
                  "competencia": "2025-10",
                  "antes": "33",
                  "depois": "8",
                  "motivo": "Tentativa"
                }
                """;

        mockMvc.perform(post("/api/chat/chat-01/correcoes")
                        .header("Authorization", bearer(sergio))
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("A conversa chat-01 pertence a outro usuário"));

        verifyNoInteractions(solicitacaoAlteracaoService);
    }

    @Test
    void postCorrecoesEmConversaInexistenteRetorna404() throws Exception {
        doThrow(new NotFoundException("Conversa chat-inexistente não encontrada"))
                .when(chatService).validarPosseConversa(eq("chat-inexistente"), any(Usuario.class));

        String payload = """
                {
                  "tipo": "TRANSFERENCIA_LOJA",
                  "matricula": "MATRIC-38",
                  "competencia": "2025-10",
                  "antes": "33",
                  "depois": "8",
                  "motivo": "Tentativa"
                }
                """;

        mockMvc.perform(post("/api/chat/chat-inexistente/correcoes")
                        .header("Authorization", bearer(gabriela))
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Conversa chat-inexistente não encontrada"));

        verifyNoInteractions(solicitacaoAlteracaoService);
    }

    @Test
    void postCorrecoesComPerfilAuditorRetorna403() throws Exception {
        mockMvc.perform(post("/api/chat/chat-01/correcoes")
                        .header("Authorization", bearer(paulo))
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));

        verifyNoInteractions(chatService);
        verifyNoInteractions(solicitacaoAlteracaoService);
    }

    @Test
    void postCorrecoesComCorpoInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/api/chat/chat-01/correcoes")
                        .header("Authorization", bearer(gabriela))
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro de validação."));

        verifyNoInteractions(chatService);
        verifyNoInteractions(solicitacaoAlteracaoService);
    }

    private Usuario usuario(int id, String nome, String email, Perfil perfil) {
        var u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("hash-irrelevante");
        u.setPerfil(perfil);
        u.setAtivo(true);
        when(usuarioRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(Usuario u) {
        return "Bearer " + jwtService.gerarToken(u, jwtService.getExpirationDate());
    }
}
