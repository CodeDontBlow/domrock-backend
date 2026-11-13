package br.com.camplana.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import br.com.camplana.dto.usuario.UsuarioResponse;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class   SecurityPorPerfilTest extends SecurityTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    // ---------- /api/chat: Gerente e Supervisor sim, Admin não ----------
    @Test void chatGerentePermitido() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(gabriela))
                .contentType(JSON).content("{}")).andExpect(liberado());
    }
    @Test void chatSupervisorPermitido() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(sergio))
                .contentType(JSON).content("{}")).andExpect(liberado());
    }
    @Test void chatAdminRecebe403SemExecutar() throws Exception {
        // corpo vazio de propósito: o 403 tem que vir antes da validação (senão seria 400)
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(ana))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lanaClient);
    }

    // ---------- /api/simulacoes ----------
    @Test void simulacaoGerentePermitido() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(gabriela))
                .contentType(JSON).content("{\"regra\":{}}")).andExpect(liberado());
    }
    @Test void simulacaoSupervisorPermitido() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(sergio))
                .contentType(JSON).content("{\"regra\":{}}")).andExpect(liberado());
    }
    @Test void simulacaoAdminRecebe403() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(ana))
                        .contentType(JSON).content("{\"regra\":{}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lanaClient);
    }

    // ---------- /api/importacao: só Supervisor ----------
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder importar() {
        return multipart("/api/importacao")
                .file(new MockMultipartFile("arquivos", "base.csv", "text/csv", "a;b".getBytes()));
    }
    @Test void importacaoSupervisorPermitido() throws Exception {
        mockMvc.perform(importar().header("Authorization", bearer(sergio))).andExpect(liberado());
    }
//    @Test void importacaoGerenteRecebe403SemExecutar() throws Exception {
//        mockMvc.perform(importar().header("Authorization", bearer(gabriela)))
//                .andExpect(status().isForbidden());
//        verifyNoInteractions(importacaoService);
//    }
    @Test void importacaoAdminRecebe403() throws Exception {
        mockMvc.perform(importar().header("Authorization", bearer(ana)))
                .andExpect(status().isForbidden());
    }

    // ---------- /api/usuarios: só Admin ----------
    @Test void usuariosAdminPermitido() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(ana)))
                .andExpect(status().isOk());
    }
    @Test void usuariosGerenteRecebe403() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(usuarioService);
    }
    @Test void usuariosSupervisorRecebe403() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(sergio)))
                .andExpect(status().isForbidden());
    }
    @Test void criarUsuarioGerenteRecebe403() throws Exception {
        mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(gabriela))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void deletarUsuarioSupervisorRecebe403() throws Exception {
        mockMvc.perform(delete("/api/usuarios/1").header("Authorization", bearer(sergio)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test void deletarUsuarioAdminPermitido() throws Exception {
        when(usuarioService.desativar(anyInt(), anyInt())).thenReturn(new Usuario());
        when(usuarioMapper.toResponse(any())).thenReturn(new UsuarioResponse(1, "x", "x@x.com", Perfil.SUPERVISOR, false));
        mockMvc.perform(delete("/api/usuarios/1").header("Authorization", bearer(ana)))
                .andExpect(liberado());
    }

    @Test void reativarUsuarioGerenteRecebe403() throws Exception {
        mockMvc.perform(patch("/api/usuarios/1/reativar").header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test void reativarUsuarioAdminPermitido() throws Exception {
        when(usuarioService.reativar(anyInt())).thenReturn(new Usuario());
        when(usuarioMapper.toResponse(any())).thenReturn(new UsuarioResponse(1, "x", "x@x.com", Perfil.SUPERVISOR, true));
        mockMvc.perform(patch("/api/usuarios/1/reativar").header("Authorization", bearer(ana)))
                .andExpect(liberado());
    }

    // ---------- AUDITOR: leituras permitidas ----------
    @Test void auditorPodeLerMensagensDoChat() throws Exception {
        mockMvc.perform(get("/api/chat/chat-123/mensagens").header("Authorization", bearer(paulo)))
                .andExpect(liberado());
    }

    @Test void auditorPodeAcessarRotaDeAuditoriaDeCampanhas() throws Exception {
        mockMvc.perform(get("/api/campanhas/auditoria").header("Authorization", bearer(paulo)))
                .andExpect(liberado());
    }

    @Test void auditorPodeLerDetalhesDeCampanha() throws Exception {
        mockMvc.perform(get("/api/campanhas/1").header("Authorization", bearer(paulo)))
                .andExpect(liberado());
    }

    @Test void auditorPodeLerVersaoDeCampanha() throws Exception {
        mockMvc.perform(get("/api/campanhas/1/versoes/1").header("Authorization", bearer(paulo)))
                .andExpect(liberado());
    }

    @Test void auditorPodeLerCampanhasAprovadas() throws Exception {
        mockMvc.perform(get("/api/campanhas/aprovadas").header("Authorization", bearer(paulo)))
                .andExpect(liberado());
    }

    @Test void gerenteNaoPodeAcessarRotaDeAuditoria() throws Exception {
        mockMvc.perform(get("/api/campanhas/auditoria").header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
    }

    @Test void supervisorNaoPodeAcessarRotaDeAuditoria() throws Exception {
        mockMvc.perform(get("/api/campanhas/auditoria").header("Authorization", bearer(sergio)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
    }

    // ---------- AUDITOR: não pode escrever nada (403 em todas as rotas de escrita) ----------
    @Test void auditorNaoPodeConversarNoChat() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(chatService);
    }

    @Test void auditorNaoPodeCriarSimulacao() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(simulacaoManagerService);
    }

    @Test void auditorNaoPodeSalvarCampanha() throws Exception {
        mockMvc.perform(post("/api/campanhas").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaService);
    }

    @Test void auditorNaoPodeRenomearCampanha() throws Exception {
        mockMvc.perform(put("/api/campanhas/1").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{\"nome\":\"Novo Nome\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaService);
    }

    @Test void auditorNaoPodeSubmeterCampanha() throws Exception {
        mockMvc.perform(post("/api/campanhas/1/submeter").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaService);
    }

    @Test void auditorNaoPodeDecidirCampanha() throws Exception {
        mockMvc.perform(post("/api/campanhas/1/decidir").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaService);
    }

    @Test void auditorNaoPodeResimularVersao() throws Exception {
        mockMvc.perform(post("/api/campanhas/1/versoes/1/resimular").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(resimulacaoService);
    }

    @Test void auditorNaoPodeEditarCampanha() throws Exception {
        mockMvc.perform(post("/api/campanhas/1/editar").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaService);
    }

    @Test void auditorNaoPodeArquivarCampanha() throws Exception {
        mockMvc.perform(post("/api/campanhas/1/arquivar").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaService);
    }

    @Test void auditorNaoAcessaFilaDeAprovacao() throws Exception {
        mockMvc.perform(get("/api/campanhas/fila").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(campanhaConsultaService);
    }

    @Test void auditorNaoPodeCriarUsuario() throws Exception {
        mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(usuarioService);
    }

    @Test void auditorNaoPodeAtualizarUsuario() throws Exception {
        mockMvc.perform(put("/api/usuarios/1").header("Authorization", bearer(paulo))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(usuarioService);
    }

    @Test void auditorNaoPodeDeletarUsuario() throws Exception {
        mockMvc.perform(delete("/api/usuarios/1").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(usuarioService);
    }

    @Test void auditorNaoPodeReativarUsuario() throws Exception {
        mockMvc.perform(patch("/api/usuarios/1/reativar").header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(usuarioService);
    }

    @Test void auditorNaoPodeImportarDados() throws Exception {
        mockMvc.perform(importar().header("Authorization", bearer(paulo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        verifyNoInteractions(managerService);
    }

    // ---------- rota não mapeada: denyAll ----------
    @Test void rotaNaoMapeadaEhNegadaParaTodos() throws Exception {
        mockMvc.perform(get("/api/qualquer-coisa").header("Authorization", bearer(ana)))
                .andExpect(status().isForbidden());
    }
}
