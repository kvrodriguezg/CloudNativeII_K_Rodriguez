package com.biblioteca.functions;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class ConsumidorFunction {

    private static final String DB_URL = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    @FunctionName("ConsumidorFunction")
    public void run(
            @EventGridTrigger(name = "eventGridEvent") String message,
            final ExecutionContext context) {
        context.getLogger().info("ConsumidorFunction – Evento recibido: " + message);

        try {
            // JSON con "eventType" y "data"
            JsonObject envelope = gson.fromJson(message, JsonObject.class);
            String eventType = envelope.get("eventType").getAsString();
            JsonObject data = envelope.getAsJsonObject("data");

            switch (eventType) {
                case "Prestamo.Creado":
                    manejarPrestamoCreado(data, context);
                    break;
                case "Usuario.Eliminado":
                    manejarUsuarioEliminado(data, context);
                    break;
                default:
                    context.getLogger().warning(
                            "ConsumidorFunction – Tipo de evento no manejado: " + eventType);
            }

        } catch (Exception e) {
            context.getLogger().severe(
                    "ConsumidorFunction – Error al procesar el evento: " + e.getMessage());
        }
    }

    // Automatización 1: Restar disponibilidad al crear un préstamo
    private void manejarPrestamoCreado(JsonObject data, ExecutionContext context) {
        String tituloLibro = data.get("tituloLibro").getAsString();
        context.getLogger().info(
                "ConsumidorFunction [Prestamo.Creado] – Restando disponibilidad del libro: " + tituloLibro);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE libros SET disponibilidad = disponibilidad - 1 WHERE titulo = ?")) {

            stmt.setString(1, tituloLibro);
            int rows = stmt.executeUpdate();

            if (rows > 0) {
                context.getLogger().info(
                        "ConsumidorFunction [Prestamo.Creado] – Disponibilidad actualizada correctamente " +
                                "para el libro: " + tituloLibro);
            } else {
                context.getLogger().warning(
                        "ConsumidorFunction [Prestamo.Creado] – No se encontró el libro con título: " + tituloLibro +
                                ". La disponibilidad NO fue actualizada.");
            }

        } catch (Exception e) {
            context.getLogger().severe(
                    "ConsumidorFunction [Prestamo.Creado] – Error en BD: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // Automatización 2: Eliminación en cascada al eliminar un usuario

    private void manejarUsuarioEliminado(JsonObject data, ExecutionContext context) {
        int idUsuario = data.get("idUsuario").getAsInt();
        context.getLogger().info(
                "ConsumidorFunction [Usuario.Eliminado] – Iniciando eliminación en cascada para id_usuario: "
                        + idUsuario);

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Eliminar los préstamos del usuario (respeta la FK)
            try (PreparedStatement stmtPrestamos = conn.prepareStatement(
                    "DELETE FROM prestamos WHERE id_usuario = ?")) {
                stmtPrestamos.setInt(1, idUsuario);
                int prestamosBorrados = stmtPrestamos.executeUpdate();
                context.getLogger().info(
                        "ConsumidorFunction [Usuario.Eliminado] – Préstamos eliminados: " + prestamosBorrados);
            }

            // Eliminar al usuario
            try (PreparedStatement stmtUsuario = conn.prepareStatement(
                    "DELETE FROM usuarios WHERE id_usuario = ?")) {
                stmtUsuario.setInt(1, idUsuario);
                int usuariosBorrados = stmtUsuario.executeUpdate();
                if (usuariosBorrados > 0) {
                    context.getLogger().info(
                            "ConsumidorFunction [Usuario.Eliminado] – Usuario " + idUsuario
                                    + " eliminado correctamente.");
                } else {
                    context.getLogger().warning(
                            "ConsumidorFunction [Usuario.Eliminado] – No se encontró el usuario con id: " + idUsuario);
                }
            }

            conn.commit(); // Confirmar toda la transacción
            context.getLogger().info(
                    "ConsumidorFunction [Usuario.Eliminado] – Transacción COMMIT exitosa para id_usuario: "
                            + idUsuario);

        } catch (Exception e) {
            context.getLogger().severe(
                    "ConsumidorFunction [Usuario.Eliminado] – Error en BD, ejecutando ROLLBACK: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    context.getLogger().severe(
                            "ConsumidorFunction [Usuario.Eliminado] – Error en ROLLBACK: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    context.getLogger().warning(
                            "ConsumidorFunction – Error al cerrar conexión: " + closeEx.getMessage());
                }
            }
        }
    }
}