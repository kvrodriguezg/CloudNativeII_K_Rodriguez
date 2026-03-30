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

    @Value("${azure.functions.baseUrl:https://appcloudnativeii-e6bxa8fqcvgreveu.eastus2-01.azurewebsites.net}")
    private String azureFunctionsBaseUrl;

    public BffController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping(value = "/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getUsuarios() {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction";
        return restTemplate.getForEntity(url, String.class);
    }

    @GetMapping(value = "/usuarios/{idUsuario}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getUsuarioById(@PathVariable String idUsuario) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction?id_usuario=" + idUsuario;
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

    @PutMapping(value = "/usuarios/{idUsuario}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateUsuario(@PathVariable String idUsuario, @RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction?id_usuario=" + idUsuario;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
    }

    @DeleteMapping(value = "/usuarios/{idUsuario}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteUsuario(@PathVariable String idUsuario) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction?id_usuario=" + idUsuario;
        return restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
    }

    @GetMapping(value = "/prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPrestamos() {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction";
        return restTemplate.getForEntity(url, String.class);
    }

    @GetMapping(value = "/prestamos/{idPrestamo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPrestamoById(@PathVariable String idPrestamo) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction?id_prestamo=" + idPrestamo;
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

    @PutMapping(value = "/prestamos/{idPrestamo}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updatePrestamo(@PathVariable String idPrestamo, @RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction?id_prestamo=" + idPrestamo;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
    }

    @DeleteMapping(value = "/prestamos/{idPrestamo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deletePrestamo(@PathVariable String idPrestamo) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction?id_prestamo=" + idPrestamo;
        return restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
    }
}
