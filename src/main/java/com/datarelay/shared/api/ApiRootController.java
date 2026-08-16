package com.datarelay.shared.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Hidden
public class ApiRootController {

    @GetMapping("/")
    ResponseEntity<Void> redirecionarParaDocumentacao() {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/swagger-ui.html"))
            .build();
    }
}
