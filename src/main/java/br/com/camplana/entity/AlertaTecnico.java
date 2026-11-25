package br.com.camplana.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "alerta_tecnico")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaTecnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @Column(name = "numero_versao", nullable = false)
    private int numeroVersao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemAlerta origem;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAlerta status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aberto_por")
    private Usuario abertoPor;

    @Column(name = "aberto_em", nullable = false)
    private Instant abertoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolvido_por")
    private Usuario resolvidoPor;

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    @Column(columnDefinition = "text")
    private String justificativa;

    public AlertaTecnico(Campanha campanha, int numeroVersao, OrigemAlerta origem, String tipo,
                         String descricao, StatusAlerta status, Usuario abertoPor, Instant abertoEm,
                         Usuario resolvidoPor, Instant resolvidoEm, String justificativa) {
        this.campanha = campanha;
        this.numeroVersao = numeroVersao;
        this.origem = origem;
        this.tipo = tipo;
        this.descricao = descricao;
        this.status = status;
        this.abertoPor = abertoPor;
        this.abertoEm = abertoEm;
        this.resolvidoPor = resolvidoPor;
        this.resolvidoEm = resolvidoEm;
        this.justificativa = justificativa;
    }
}
