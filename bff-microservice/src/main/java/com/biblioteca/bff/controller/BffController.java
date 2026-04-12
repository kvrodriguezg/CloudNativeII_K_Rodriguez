package com.biblioteca.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    private final RestTemplate restTemplate;

    @Value("${azure.functions.baseUrl:https://appcloudnativeii-e6bxa8fqcvgreveu.eastus2-01.azurewebsites.net}")
    private String azureFunctionsBaseUrl;

    public BffController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    //USUARIOS
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
        return ejecutarPeticion(url, HttpMethod.POST, requestBody);
    }

    @PutMapping(value = "/usuarios/{idUsuario}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateUsuario(@PathVariable String idUsuario, @RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction?id_usuario=" + idUsuario;
        return ejecutarPeticion(url, HttpMethod.PUT, requestBody);
    }

    @DeleteMapping(value = "/usuarios/{idUsuario}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteUsuario(@PathVariable String idUsuario) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosFunction?id_usuario=" + idUsuario;
        return ejecutarPeticion(url, HttpMethod.DELETE, null);
    }

    //PRESTAMOS

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
        return ejecutarPeticion(url, HttpMethod.POST, requestBody);
    }

    @PutMapping(value = "/prestamos/{idPrestamo}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updatePrestamo(@PathVariable String idPrestamo, @RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction?id_prestamo=" + idPrestamo;
        return ejecutarPeticion(url, HttpMethod.PUT, requestBody);
    }

    @DeleteMapping(value = "/prestamos/{idPrestamo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deletePrestamo(@PathVariable String idPrestamo) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosFunction?id_prestamo=" + idPrestamo;
        return ejecutarPeticion(url, HttpMethod.DELETE, null);
    }

    // GRAPHQL

    @PostMapping(value = "/graphql/usuarios", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> executeGraphQLUsuarios(@RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/UsuariosGraphQLFunction";
        return ejecutarPeticion(url, HttpMethod.POST, requestBody);
    }

    @PostMapping(value = "/graphql/prestamos", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> executeGraphQLPrestamos(@RequestBody String requestBody) {
        String url = azureFunctionsBaseUrl + "/api/PrestamosGraphQLFunction";
        return ejecutarPeticion(url, HttpMethod.POST, requestBody);
    }

    private ResponseEntity<String> ejecutarPeticion(String url, HttpMethod metodo, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, metodo, entity, String.class);
    }
}