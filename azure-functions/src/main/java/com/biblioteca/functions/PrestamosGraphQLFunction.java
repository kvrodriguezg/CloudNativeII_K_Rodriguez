package com.biblioteca.functions;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.sql.Connection;
import java.sql.Date;
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

public class PrestamosGraphQLFunction {

    private static final Logger LOGGER = Logger.getLogger(PrestamosGraphQLFunction.class.getName());

    private static final String DB_URL = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    // Constantes GraphQL / Map
    private static final String FIELD_ID_PRESTAMO = "id_prestamo";
    private static final String FIELD_ID_USUARIO = "id_usuario";
    private static final String FIELD_NOMBRE = "nombre";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_LIBRO = "libro";
    private static final String FIELD_FECHA = "fecha_prestamo";
    private static final String FIELD_USUARIO = "usuario";
    private static final String FIELD_ERROR = "error";
    private static final String GRAPHQL_VARIABLES = "variables";

    // Constantes columna DB
    private static final String COL_ID_PRESTAMO = "ID_PRESTAMO";
    private static final String COL_ID_USUARIO = "ID_USUARIO";

    // Constantes HTTP
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String MIME_JSON = "application/json";

    private static GraphQL buildGraphQL() {
        String schema = "type Query {"
                + "  usuario(id_usuario: Int): Usuario "
                + "  usuarios: [Usuario] "
                + "  prestamo(id_prestamo: Int): Prestamo "
                + "  prestamos: [Prestamo] "
                + "} "
                + "type Usuario {"
                + "  id_usuario: Int "
                + "  nombre: String "
                + "  email: String "
                + "  prestamos: [Prestamo] "
                + "} "
                + "type Prestamo {"
                + "  id_prestamo: Int "
                + "  id_usuario: Int "
                + "  libro: String "
                + "  fecha_prestamo: String "
                + "  usuario: Usuario "
                + "}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("prestamo", getPrestamoDataFetcher())
                        .dataFetcher("prestamos", getPrestamosDataFetcher()))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static DataFetcher<Map<String, Object>> getPrestamoDataFetcher() {
        return dataFetchingEnvironment -> {
            Integer id = dataFetchingEnvironment.getArgument(FIELD_ID_PRESTAMO);
            if (id == null)
                return null;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(
                            "SELECT p.*, u.nombre, u.email FROM prestamos p "
                                    + "INNER JOIN usuarios u ON p.ID_USUARIO = u.ID_USUARIO "
                                    + "WHERE p.ID_PRESTAMO = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> prestamo = new HashMap<>();
                        prestamo.put(FIELD_ID_PRESTAMO, rs.getInt(COL_ID_PRESTAMO));
                        prestamo.put(FIELD_LIBRO, rs.getString(FIELD_LIBRO));
                        Date fechaPrestamo = rs.getDate(FIELD_FECHA);
                        prestamo.put(FIELD_FECHA, fechaPrestamo != null ? fechaPrestamo.toString() : null);

                        // Crear objeto usuario
                        Map<String, Object> user = new HashMap<>();
                        user.put(FIELD_ID_USUARIO, rs.getInt(COL_ID_USUARIO));
                        user.put(FIELD_NOMBRE, rs.getString(FIELD_NOMBRE));
                        user.put(FIELD_EMAIL, rs.getString(FIELD_EMAIL));

                        prestamo.put(FIELD_USUARIO, user);
                        return prestamo;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al obtener prestamo", e);
            }
            return null;
        };
    }

    private static DataFetcher<List<Map<String, Object>>> getPrestamosDataFetcher() {
        return dataFetchingEnvironment -> {
            List<Map<String, Object>> prestamos = new ArrayList<>();
            String sql = "SELECT p.*, u.nombre, u.email FROM prestamos p "
                    + "INNER JOIN usuarios u ON p.ID_USUARIO = u.ID_USUARIO";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put(FIELD_ID_PRESTAMO, rs.getInt(COL_ID_PRESTAMO));
                    p.put(FIELD_LIBRO, rs.getString(FIELD_LIBRO));
                    Date fechaPrestamo = rs.getDate(FIELD_FECHA);
                    p.put(FIELD_FECHA, fechaPrestamo != null ? fechaPrestamo.toString() : null);

                    // Crear objeto usuario anidado
                    Map<String, Object> user = new HashMap<>();
                    user.put(FIELD_ID_USUARIO, rs.getInt(COL_ID_USUARIO));
                    user.put(FIELD_NOMBRE, rs.getString(FIELD_NOMBRE));
                    user.put(FIELD_EMAIL, rs.getString(FIELD_EMAIL));

                    p.put(FIELD_USUARIO, user);
                    prestamos.add(p);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al obtener prestamos", e);
            }
            return prestamos;
        };
    }

    private static final GraphQL graphQL = buildGraphQL();

    @FunctionName("PrestamosGraphQLFunction")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        try {
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            String query = jsonObject.get("query").getAsString();

            Map<String, Object> variables = new HashMap<>();
            if (jsonObject.has(GRAPHQL_VARIABLES) && !jsonObject.get(GRAPHQL_VARIABLES).isJsonNull()) {
                variables = gson.fromJson(jsonObject.get(GRAPHQL_VARIABLES),
                        new TypeToken<Map<String, Object>>() {
                        }.getType());
            }

            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();

            ExecutionResult executionResult = graphQL.execute(executionInput);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header(HEADER_CONTENT_TYPE, MIME_JSON)
                    .body(gson.toJson(executionResult.toSpecification()))
                    .build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en PrestamosGraphQLFunction", e);
            Map<String, String> err = new HashMap<>();
            err.put(FIELD_ERROR, e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HEADER_CONTENT_TYPE, MIME_JSON)
                    .body(gson.toJson(err)).build();
        }
    }
}