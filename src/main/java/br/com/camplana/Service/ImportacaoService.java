package br.com.camplana.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImportacaoService {

    public record ResultadoImportacaoArquivo(
            String nomeArquivo,
            String tipoDetectado, // RH | VENDAS | COMISSAO | DESCONHECIDO
            int linhasProcessadas,
            int linhasDescartadas,
            List<String> erros
    ) {}

    public List<ResultadoImportacaoArquivo> importarLote(List<MultipartFile> arquivos) {
        List<ResultadoImportacaoArquivo> resultados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            resultados.add(importarArquivo(arquivo));
        }
        return resultados;
    }

    private ResultadoImportacaoArquivo importarArquivo(MultipartFile arquivo) {
        // TODO: detectar tipo pelo cabeçalho (RH tem Cod_Cargo; Vendas tem "Vlr _Venda"; Comissao tem "%_Comiss")
        // TODO: suportar .csv e .xlsx (Apache POI pro xlsx)
        // TODO: normalizar (trim de header com espaco, %_Comiss sem "%", GERENTE QUIOSQUE descartado, etc)
        // TODO: upsert idempotente (rodar duas vezes nao duplica)
        throw new UnsupportedOperationException("parsing ainda nao implementado");
    }
}