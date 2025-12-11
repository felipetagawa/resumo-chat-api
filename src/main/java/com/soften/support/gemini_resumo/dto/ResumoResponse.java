package com.soften.support.gemini_resumo.dto;

import java.util.List;

public class ResumoResponse {
    private String titulo;
    private String resumo;
    private List<String> recomendacoes;
    private List<String> documentacoesSugeridas;

    public ResumoResponse(String titulo, String resumo, List<String> recomendacoes,
            List<String> documentacoesSugeridas) {
        this.titulo = titulo;
        this.resumo = resumo;
        this.recomendacoes = recomendacoes;
        this.documentacoesSugeridas = documentacoesSugeridas;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getResumo() {
        return resumo;
    }

    public void setResumo(String resumo) {
        this.resumo = resumo;
    }

    public List<String> getRecomendacoes() {
        return recomendacoes;
    }

    public void setRecomendacoes(List<String> recomendacoes) {
        this.recomendacoes = recomendacoes;
    }

    public List<String> getDocumentacoesSugeridas() {
        return documentacoesSugeridas;
    }

    public void setDocumentacoesSugeridas(List<String> documentacoesSugeridas) {
        this.documentacoesSugeridas = documentacoesSugeridas;
    }
}
