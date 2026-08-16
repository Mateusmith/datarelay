package com.datarelay.shared.api;

import com.datarelay.shared.domain.ConflictException;
import com.datarelay.shared.domain.DomainException;
import com.datarelay.shared.domain.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger REGISTRADOR = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> tratarNaoEncontrado(NotFoundException excecao, HttpServletRequest requisicao) {
        return problema(HttpStatus.NOT_FOUND, excecao.getMessage(), requisicao);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> tratarConflito(ConflictException excecao, HttpServletRequest requisicao) {
        return problema(HttpStatus.CONFLICT, excecao.getMessage(), requisicao);
    }

    @ExceptionHandler({DomainException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> tratarRequisicaoInvalida(RuntimeException excecao, HttpServletRequest requisicao) {
        return problema(HttpStatus.BAD_REQUEST, excecao.getMessage(), requisicao);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> tratarValidacao(MethodArgumentNotValidException excecao,
                                                    HttpServletRequest requisicao) {
        ProblemDetail detalhe = criarProblema(HttpStatus.BAD_REQUEST, "Falha na validacao da requisicao", requisicao);
        List<Map<String, String>> violacoes = excecao.getBindingResult().getFieldErrors().stream()
            .map(erro -> Map.of(
                "campo", erro.getField(),
                "mensagem", erro.getDefaultMessage() == null ? "valor invalido" : erro.getDefaultMessage()))
            .toList();
        detalhe.setProperty("violacoes", violacoes);
        return ResponseEntity.badRequest().body(detalhe);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> tratarParametroDeUrlInvalido(MethodArgumentTypeMismatchException excecao,
                                                                 HttpServletRequest requisicao) {
        String mensagem = "Parametro de URL invalido: " + excecao.getName();
        return problema(HttpStatus.BAD_REQUEST, mensagem, requisicao);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> tratarJsonInvalido(HttpMessageNotReadableException excecao,
                                                       HttpServletRequest requisicao) {
        return problema(HttpStatus.BAD_REQUEST, "JSON ausente ou malformado", requisicao);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> tratarInesperado(Exception excecao, HttpServletRequest requisicao) {
        REGISTRADOR.error("Falha inesperada ao processar a requisicao", excecao);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado", requisicao);
    }

    private ResponseEntity<ProblemDetail> problema(HttpStatus status, String mensagem, HttpServletRequest requisicao) {
        return ResponseEntity.status(status).body(criarProblema(status, mensagem, requisicao));
    }

    private ProblemDetail criarProblema(HttpStatus status, String mensagem, HttpServletRequest requisicao) {
        ProblemDetail detalhe = ProblemDetail.forStatusAndDetail(status, mensagem);
        detalhe.setTitle(titulo(status));
        detalhe.setInstance(URI.create(requisicao.getRequestURI()));
        Object idCorrelacao = requisicao.getAttribute(CorrelationIdFilter.NOME_ATRIBUTO);
        if (idCorrelacao != null) {
            detalhe.setProperty("idCorrelacao", idCorrelacao);
        }
        return detalhe;
    }

    private String titulo(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Requisicao invalida";
            case NOT_FOUND -> "Recurso nao encontrado";
            case CONFLICT -> "Conflito";
            case INTERNAL_SERVER_ERROR -> "Erro interno do servidor";
            default -> status.getReasonPhrase();
        };
    }
}
