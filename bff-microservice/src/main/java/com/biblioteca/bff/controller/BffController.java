package com.biblioteca.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    private final RestTemplate restTemplate;

    @Value("${azure.functions.baseUrl:http://localhost:7071}")
    private String azureFunctionsBaseUrl;

    public BffController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping(value = "/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getUsuarios() {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction";
        return restTemplate.getForEntity(url, String.class);
    }

    @PostMapping(value = "/usuarios", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createUsuario(@RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    // --- NUEVO: Endpoints de Prestamos ---

    @GetMapping(value = "/prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPrestamos() {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction";
        return restTemplate.getForEntity(url, String.class);
    }

    @PostMapping(value = "/prestamos", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createPrestamo(@RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }
}
