package br.com.camplana.Entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FuncionarioLojaId implements Serializable {

    private Integer funcionarioId;
    private Integer lojaId;
    private LocalDate dateRef;
}