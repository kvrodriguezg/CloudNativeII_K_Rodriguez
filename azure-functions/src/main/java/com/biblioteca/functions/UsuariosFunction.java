package com.biblioteca.functions;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UsuariosFunction {

    private static final String DB_URL = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    @FunctionName("UsuariosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        if (request.getHttpMethod() == HttpMethod.GET) {
            return getUsuarios(request);
        } else if (request.getHttpMethod() == HttpMethod.POST) {
            return createUsuario(request);
        } else if (request.getHttpMethod() == HttpMethod.PUT) {
            return updateUsuario(request);
        } else if (request.getHttpMethod() == HttpMethod.DELETE) {
            return deleteUsuario(request);
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();
    }

    private HttpResponseMessage getUsuarios(HttpRequestMessage<?> request) {
        String idParam = request.getQueryParameters().get("id_usuario");
        List<Map<String, Object>> usuarios = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            PreparedStatement stmt;
            if (idParam != null && !idParam.isEmpty()) {
                stmt = conn.prepareStatement("SELECT ID_USUARIO, nombre, email FROM usuarios WHERE ID_USUARIO = ?");
                stmt.setInt(1, Integer.parseInt(idParam));
            } else {
                stmt = conn.prepareStatement("SELECT ID_USUARIO, nombre, email FROM usuarios");
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> usuario = new HashMap<>();
                    usuario.put("id_usuario", rs.getInt("ID_USUARIO"));
                    usuario.put("nombre", rs.getString("nombre"));
                    usuario.put("email", rs.getString("email"));
                    usuarios.add(usuario);
                }
            }
            stmt.close();
            
            if (idParam != null && !idParam.isEmpty() && usuarios.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"Not Found\"}").build();
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(idParam != null && !idParam.isEmpty() && !usuarios.isEmpty() ? gson.toJson(usuarios.get(0)) : gson.toJson(usuarios))
                    .build();
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(err)).build();
        }
    }

    private HttpResponseMessage createUsuario(HttpRequestMessage<Optional<String>> request) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            String nombre = jsonObject.get("nombre").getAsString();
            String email = jsonObject.get("email").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO usuarios (nombre, email) VALUES (?, ?)")) {
                stmt.setString(1, nombre);
                stmt.setString(2, email);
                stmt.executeUpdate();
                
                return request.createResponseBuilder(HttpStatus.CREATED)
                        .header("Content-Type", "application/json")
                        .body("{\"message\":\"Created\"}").build();
            }
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(err)).build();
        }
    }

    private HttpResponseMessage updateUsuario(HttpRequestMessage<Optional<String>> request) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            
            String idParam = request.getQueryParameters().get("id_usuario");
            int idUsuario;
            if (idParam != null && !idParam.isEmpty()) {
                idUsuario = Integer.parseInt(idParam);
            } else {
                idUsuario = jsonObject.get("id_usuario").getAsInt();
            }
            
            String nombre = jsonObject.get("nombre").getAsString();
            String email = jsonObject.get("email").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("UPDATE usuarios SET nombre = ?, email = ? WHERE ID_USUARIO = ?")) {
                stmt.setString(1, nombre);
                stmt.setString(2, email);
                stmt.setInt(3, idUsuario);
                int rows = stmt.executeUpdate();
                
                if (rows > 0) {
                    return request.createResponseBuilder(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"message\":\"Updated\"}").build();
                } else {
                    return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                            .header("Content-Type", "application/json")
                            .body("{\"error\":\"Not Found\"}").build();
                }
            }
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(err)).build();
        }
    }

    private HttpResponseMessage deleteUsuario(HttpRequestMessage<Optional<String>> request) {
        try {
            String idParam = request.getQueryParameters().get("id_usuario");
            if (idParam == null) {
                String body = request.getBody().orElse("");
                JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
                idParam = jsonObject.get("id_usuario").getAsString();
            }
            int idUsuario = Integer.parseInt(idParam);

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM usuarios WHERE ID_USUARIO = ?")) {
                stmt.setInt(1, idUsuario);
                int rows = stmt.executeUpdate();
                
                if (rows > 0) {
                    return request.createResponseBuilder(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"message\":\"Deleted\"}").build();
                } else {
                    return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                            .header("Content-Type", "application/json")
                            .body("{\"error\":\"Not Found\"}").build();
                }
            }
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(err)).build();
        }
    }
}
