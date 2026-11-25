package br.com.camplana.controller;

import br.com.camplana.campanha.AlertaTecnicoService;
import br.com.camplana.campanha.CampanhaConsultaService;
import br.com.camplana.campanha.CampanhaService;
import br.com.camplana.campanha.ResimulacaoService;
import br.com.camplana.dto.campanha.*;
import br.com.camplana.dto.simulacao.SimulacaoCriadaResponse;
import br.com.camplana.entity.Usuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Perfis na matriz do SecurityConfig (a fila é só SUPERVISOR); autor e status, em PermissoesCampanha.
@RestController
@RequestMapping("/api/campanhas")
public class CampanhaController {

    private final CampanhaService campanhaService;
    private final CampanhaConsultaService consulta;
    private final ResimulacaoService resimulacao;
    private final AlertaTecnicoService alertaTecnicoService;

    public CampanhaController(CampanhaService campanhaService, CampanhaConsultaService consulta,
                              ResimulacaoService resimulacao, AlertaTecnicoService alertaTecnicoService) {
        this.campanhaService = campanhaService;
        this.consulta = consulta;
        this.resimulacao = resimulacao;
        this.alertaTecnicoService = alertaTecnicoService;
    }

    @PostMapping
    public ResponseEntity<CampanhaResponse> salvar(@Valid @RequestBody SalvarCampanhaRequest pedido,
                                                   @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campanhaService.salvar(pedido, usuario));
    }

    @GetMapping("/minhas")
    public List<CampanhaResumoResponse> minhas(@RequestParam(required = false) String status,
                                               @AuthenticationPrincipal Usuario usuario) {
        return consulta.minhas(usuario, status);
    }

    @GetMapping("/fila")
    public List<FilaCampanhaResponse> fila(@AuthenticationPrincipal Usuario usuario) {
        return consulta.fila(usuario);
    }

    @GetMapping("/aprovadas")
    public List<CampanhaAprovadaResponse> aprovadas() {
        return consulta.aprovadas();
    }

    @GetMapping("/auditoria")
    public List<CampanhaResumoResponse> auditoria(@RequestParam(required = false) String status) {
        return consulta.todas(status);
    }

    @GetMapping("/{id}")
    public CampanhaResponse abrir(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return consulta.abrir(id, usuario);
    }

    @GetMapping("/{id}/versoes/{numero}")
    public VersaoCampanhaResponse versao(@PathVariable long id, @PathVariable int numero,
                                         @AuthenticationPrincipal Usuario usuario) {
        return consulta.versao(id, numero, usuario);
    }

    @GetMapping("/{id}/versoes/{numero}/alertas")
    public List<AlertaTecnicoResponse> listarAlertas(@PathVariable long id, @PathVariable int numero,
                                                    @AuthenticationPrincipal Usuario usuario) {
        return alertaTecnicoService.listar(id, numero, usuario);
    }

    @PostMapping("/{id}/versoes/{numero}/alertas")
    public ResponseEntity<AlertaTecnicoResponse> abrirAlerta(@PathVariable long id, @PathVariable int numero,
                                                             @Valid @RequestBody CriarAlertaRequest pedido,
                                                             @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertaTecnicoService.abrir(id, numero, pedido, usuario));
    }

    @PostMapping("/{id}/versoes/{numero}/alertas/{alertaId}/resolver")
    public AlertaTecnicoResponse resolverAlerta(@PathVariable long id, @PathVariable int numero,
                                                @PathVariable long alertaId,
                                                @RequestBody(required = false) ResolverAlertaRequest pedido,
                                                @AuthenticationPrincipal Usuario usuario) {
        return alertaTecnicoService.resolver(id, numero, alertaId, pedido, usuario);
    }

    @PutMapping("/{id}")
    public CampanhaResponse renomear(@PathVariable long id, @RequestBody RenomearCampanhaRequest pedido,
                                     @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.renomear(id, pedido.nome(), usuario);
    }

    @PostMapping("/{id}/submeter")
    public CampanhaResponse submeter(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.submeter(id, usuario);
    }

    @PostMapping("/{id}/decidir")
    public CampanhaResponse decidir(@PathVariable long id, @Valid @RequestBody DecidirCampanhaRequest pedido,
                                    @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.decidir(id, pedido, usuario);
    }

    @PostMapping("/{id}/versoes/{numero}/resimular")
    public ResponseEntity<SimulacaoCriadaResponse> resimular(@PathVariable long id, @PathVariable int numero,
                                                             @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resimulacao.resimular(id, numero, usuario));
    }

    @PostMapping("/{id}/editar")
    public CampanhaResponse editar(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.editar(id, usuario);
    }

    @PostMapping("/{id}/arquivar")
    public CampanhaResponse arquivar(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.arquivar(id, usuario);
    }
}
