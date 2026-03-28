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
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Iniciando PrestamosFunction.");

        if (request.getHttpMethod() == HttpMethod.GET) {
            return getPrestamos(request, context);
        } else if (request.getHttpMethod() == HttpMethod.POST) {
            return createPrestamo(request, context);
        }

        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();
    }

    private HttpResponseMessage getPrestamos(HttpRequestMessage<?> request, ExecutionContext context) {
        List<Map<String, Object>> prestamos = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement("SELECT id, id_usuario, libro, fecha_prestamo FROM prestamos");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> prestamo = new HashMap<>();
                prestamo.put("id", rs.getInt("id"));
                prestamo.put("id_usuario", rs.getInt("id_usuario"));
                prestamo.put("libro", rs.getString("libro"));
                prestamo.put("fecha_prestamo", rs.getDate("fecha_prestamo") != null ? rs.getDate("fecha_prestamo").toString() : null);
                prestamos.add(prestamo);
            }
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(prestamos))
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Error al consultar prestamos: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error interno al conectar con base de datos.");
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(error))
                    .build();
        }
    }

    private HttpResponseMessage createPrestamo(HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            int idUsuario = jsonObject.get("id_usuario").getAsInt();
            String libro = jsonObject.get("libro").getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO prestamos (id_usuario, libro, fecha_prestamo) VALUES (?, ?, SYSDATE)")) {
                
                stmt.setInt(1, idUsuario);
                stmt.setString(2, libro);
                stmt.executeUpdate();

                Map<String, String> response = new HashMap<>();
                response.put("message", "Préstamo creado con éxito");
                return request.createResponseBuilder(HttpStatus.CREATED)
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(response))
                        .build();
            }
        } catch (Exception e) {
            context.getLogger().severe("Error al crear prestamo: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error en formato del JSON o base de datos.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(error))
                    .build();
        }
    }
}
