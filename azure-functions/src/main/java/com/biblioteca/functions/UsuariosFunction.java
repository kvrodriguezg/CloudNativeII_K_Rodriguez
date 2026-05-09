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

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsuariosFunction {

    private static final Logger LOGGER = Logger.getLogger(UsuariosFunction.class.getName());

    private static final String DB_URL = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    // Constantes de campo/clave
    private static final String FIELD_ID_USUARIO = "id_usuario";
    private static final String FIELD_NOMBRE = "nombre";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_ERROR = "error";

    // Constantes de encabezado/valor HTTP
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String MIME_JSON = "application/json";

    // Constantes de columna DB
    private static final String COL_ID_USUARIO = "ID_USUARIO";

    @FunctionName("UsuariosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                    HttpMethod.DELETE }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        if (request.getHttpMethod() == HttpMethod.GET) {
            return getUsuarios(request);
        } else if (request.getHttpMethod() == HttpMethod.POST) {
            return createUsuario(request);
        } else if (request.getHttpMethod() == HttpMethod.PUT) {
            return updateUsuario(request);
        } else if (request.getHttpMethod() == HttpMethod.DELETE) {
            return deleteUsuario(request, context);
        }
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).build();
    }

    private HttpResponseMessage getUsuarios(HttpRequestMessage<?> request) {
        String idParam = request.getQueryParameters().get(FIELD_ID_USUARIO);
        boolean hasId = idParam != null && !idParam.isEmpty();
        List<Map<String, Object>> usuarios = new ArrayList<>();

        String sql = hasId
                ? "SELECT ID_USUARIO, nombre, email FROM usuarios WHERE ID_USUARIO = ?"
                : "SELECT ID_USUARIO, nombre, email FROM usuarios";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (hasId) {
                stmt.setInt(1, Integer.parseInt(idParam));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> usuario = new HashMap<>();
                    usuario.put(FIELD_ID_USUARIO, rs.getInt(COL_ID_USUARIO));
                    usuario.put(FIELD_NOMBRE, rs.getString(FIELD_NOMBRE));
                    usuario.put(FIELD_EMAIL, rs.getString(FIELD_EMAIL));
                    usuarios.add(usuario);
                }
            }

            if (hasId && usuarios.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header(HEADER_CONTENT_TYPE, MIME_JSON)
                        .body("{\"error\":\"Not Found\"}").build();
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header(HEADER_CONTENT_TYPE, MIME_JSON)
                    .body(hasId && !usuarios.isEmpty()
                            ? gson.toJson(usuarios.get(0))
                            : gson.toJson(usuarios))
                    .build();
        } catch (Exception e) {
            return buildErrorResponse(request, e);
        }
    }

    private HttpResponseMessage createUsuario(HttpRequestMessage<Optional<String>> request) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            String nombre = jsonObject.get(FIELD_NOMBRE).getAsString();
            String email = jsonObject.get(FIELD_EMAIL).getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO usuarios (nombre, email) VALUES (?, ?)")) {
                stmt.setString(1, nombre);
                stmt.setString(2, email);
                stmt.executeUpdate();

                return request.createResponseBuilder(HttpStatus.CREATED)
                        .header(HEADER_CONTENT_TYPE, MIME_JSON)
                        .body("{\"message\":\"Created\"}").build();
            }
        } catch (Exception e) {
            return buildErrorResponse(request, e);
        }
    }

    private HttpResponseMessage updateUsuario(HttpRequestMessage<Optional<String>> request) {
        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);

            String idParam = request.getQueryParameters().get(FIELD_ID_USUARIO);
            int idUsuario;
            if (idParam != null && !idParam.isEmpty()) {
                idUsuario = Integer.parseInt(idParam);
            } else {
                idUsuario = jsonObject.get(FIELD_ID_USUARIO).getAsInt();
            }

            String nombre = jsonObject.get(FIELD_NOMBRE).getAsString();
            String email = jsonObject.get(FIELD_EMAIL).getAsString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE usuarios SET nombre = ?, email = ? WHERE ID_USUARIO = ?")) {
                stmt.setString(1, nombre);
                stmt.setString(2, email);
                stmt.setInt(3, idUsuario);
                int rows = stmt.executeUpdate();

                if (rows > 0) {
                    return request.createResponseBuilder(HttpStatus.OK)
                            .header(HEADER_CONTENT_TYPE, MIME_JSON)
                            .body("{\"message\":\"Updated\"}").build();
                } else {
                    return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                            .header(HEADER_CONTENT_TYPE, MIME_JSON)
                            .body("{\"error\":\"Not Found\"}").build();
                }
            }
        } catch (Exception e) {
            return buildErrorResponse(request, e);
        }
    }

    private HttpResponseMessage deleteUsuario(
            HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        try {
            String idParam = request.getQueryParameters().get(FIELD_ID_USUARIO);
            if (idParam == null || idParam.isEmpty()) {
                String body = request.getBody().orElse("");
                JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
                idParam = jsonObject.get(FIELD_ID_USUARIO).getAsString();
            }
            int idUsuario = Integer.parseInt(idParam);

            // Publicar evento "Usuario.Eliminado" en Azure
            String eventGridEndpoint = System.getenv("EVENT_GRID_ENDPOINT");
            String eventGridKey = System.getenv("EVENT_GRID_KEY");

            if (eventGridEndpoint == null || eventGridEndpoint.isEmpty()
                    || eventGridKey == null || eventGridKey.isEmpty()) {
                LOGGER.severe("Variables EVENT_GRID_ENDPOINT / EVENT_GRID_KEY no configuradas. "
                        + "No se puede publicar el evento 'Usuario.Eliminado'.");
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header(HEADER_CONTENT_TYPE, MIME_JSON)
                        .body("{\"error\":\"Event Grid no configurado. Eliminación cancelada.\"}").build();
            }

            EventGridPublisherClient<EventGridEvent> client = new EventGridPublisherClientBuilder()
                    .endpoint(eventGridEndpoint)
                    .credential(new AzureKeyCredential(eventGridKey))
                    .buildEventGridEventPublisherClient();

            // El consumidor usará idUsuario para la cascada
            UsuarioEventData eventData = new UsuarioEventData(idUsuario);

            EventGridEvent event = new EventGridEvent(
                    "/biblioteca/usuarios",
                    "Usuario.Eliminado",
                    BinaryData.fromObject(eventData),
                    "1.0");

            client.sendEvent(event);
            context.getLogger().log(Level.INFO,
                    "Evento ''Usuario.Eliminado'' publicado para id_usuario: {0}", idUsuario);

            // Eliminación real ocurrirá de forma asíncrona
            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .header(HEADER_CONTENT_TYPE, MIME_JSON)
                    .body("{\"message\":\"Solicitud de eliminación aceptada. "
                            + "El usuario y sus préstamos serán eliminados de forma asíncrona.\"}")
                    .build();

        } catch (Exception e) {
            return buildErrorResponse(request, e);
        }
    }

    private HttpResponseMessage buildErrorResponse(HttpRequestMessage<?> request, Exception e) {
        LOGGER.log(Level.SEVERE, "Error en UsuariosFunction", e);
        Map<String, String> err = new HashMap<>();
        err.put(FIELD_ERROR, e.getMessage() != null ? e.getMessage() : e.toString());
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HEADER_CONTENT_TYPE, MIME_JSON)
                .body(gson.toJson(err)).build();
    }
}
