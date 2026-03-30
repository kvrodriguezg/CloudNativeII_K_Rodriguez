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

public class PrestamosFunction {

    private static final String DB_URL = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    @FunctionName("PrestamosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        if (request.getHttpMethod() == HttpMethod.GET) {
            return getPrestamos(request);
        } else if (request.getHttpMethod() == HttpMethod.POST) {
            return createPrestamo(request);
        } else if (request.getHttpMethod() == HttpMethod.PUT) {
            return updatePrestamo(request);
        } else if (request.getHttpMethod() == HttpMethod.DELETE) {
            return deletePrestamo(request);
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();
    }

    private HttpResponseMessage getPrestamos(HttpRequestMessage<?> request) {
        String idParam = request.getQueryParameters().get("id_prestamo");
        List<Map<String, Object>> prestamos = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            PreparedStatement stmt;
            if (idParam != null && !idParam.isEmpty()) {
                stmt = conn.prepareStatement("SELECT ID_PRESTAMO, ID_USUARIO, libro, fecha_prestamo FROM prestamos WHERE ID_PRESTAMO = ?");
                stmt.setInt(1, Integer.parseInt(idParam));
            } else {
                stmt = conn.prepareStatement("SELECT ID_PRESTAMO, ID_USUARIO, libro, fecha_prestamo FROM prestamos");
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> prestamo = new HashMap<>();
                    prestamo.put("id_prestamo", rs.getInt("ID_PRESTAMO"));
                    prestamo.put("id_usuario", rs.getInt("ID_USUARIO"));
                    prestamo.put("libro", rs.getString("libro"));
                    prestamo.put("fecha_prestamo", rs.getDate("fecha_prestamo") != null ? rs.getDate("fecha_prestamo").toString() : null);
                    prestamos.add(prestamo);
                }
            }
            stmt.close();

            if (idParam != null && !idParam.isEmpty() && prestamos.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"Not Found\"}").build();
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(idParam != null && !idParam.isEmpty() && !prestamos.isEmpty() ? gson.toJson(prestamos.get(0)) : gson.toJson(prestamos)).build();
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(err)).build();
        }
    }

    private HttpResponseMessage createPrestamo(HttpRequestMessage<Optional<String>> request) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            int idUsuario = jsonObject.get("id_usuario").getAsInt();
            String libro = jsonObject.get("libro").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO prestamos (ID_USUARIO, libro, fecha_prestamo) VALUES (?, ?, SYSDATE)")) {
                stmt.setInt(1, idUsuario);
                stmt.setString(2, libro);
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

    private HttpResponseMessage updatePrestamo(HttpRequestMessage<Optional<String>> request) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            
            String idParam = request.getQueryParameters().get("id_prestamo");
            int idPrestamo;
            if (idParam != null && !idParam.isEmpty()) {
                idPrestamo = Integer.parseInt(idParam);
            } else {
                idPrestamo = jsonObject.get("id_prestamo").getAsInt();
            }
            
            int idUsuario = jsonObject.get("id_usuario").getAsInt();
            String libro = jsonObject.get("libro").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("UPDATE prestamos SET ID_USUARIO = ?, libro = ? WHERE ID_PRESTAMO = ?")) {
                stmt.setInt(1, idUsuario);
                stmt.setString(2, libro);
                stmt.setInt(3, idPrestamo);
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

    private HttpResponseMessage deletePrestamo(HttpRequestMessage<Optional<String>> request) {
        try {
            String idParam = request.getQueryParameters().get("id_prestamo");
            if (idParam == null) {
                String body = request.getBody().orElse("");
                JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
                idParam = jsonObject.get("id_prestamo").getAsString();
            }
            int idPrestamo = Integer.parseInt(idParam);

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM prestamos WHERE ID_PRESTAMO = ?")) {
                stmt.setInt(1, idPrestamo);
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
