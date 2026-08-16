package com.datarelay.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String NOME_CABECALHO = "X-Correlation-ID";
    public static final String NOME_ATRIBUTO = "idCorrelacao";

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain chain)
        throws ServletException, IOException {
        String idCorrelacao = requisicao.getHeader(NOME_CABECALHO);
        if (idCorrelacao == null || idCorrelacao.isBlank() || idCorrelacao.length() > 100) {
            idCorrelacao = UUID.randomUUID().toString();
        }

        requisicao.setAttribute(NOME_ATRIBUTO, idCorrelacao);
        resposta.setHeader(NOME_CABECALHO, idCorrelacao);
        MDC.put(NOME_ATRIBUTO, idCorrelacao);
        try {
            chain.doFilter(requisicao, resposta);
        } finally {
            MDC.remove(NOME_ATRIBUTO);
        }
    }
}
