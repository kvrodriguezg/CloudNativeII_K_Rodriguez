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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PrestamosGraphQLFunction {

    private static final String DB_URL = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    private static GraphQL buildGraphQL() {
        String schema = "type Query {" +
                "  usuario(id_usuario: Int): Usuario " +
                "  usuarios: [Usuario] " +
                "  prestamo(id_prestamo: Int): Prestamo " +
                "  prestamos: [Prestamo] " +
                "} " +
                "type Usuario {" +
                "  id_usuario: Int " +
                "  nombre: String " +
                "  email: String " +
                "  prestamos: [Prestamo] " +
                "} " +
                "type Prestamo {" +
                "  id_prestamo: Int " +
                "  id_usuario: Int " +
                "  libro: String " +
                "  fecha_prestamo: String " +
                "  usuario: Usuario " +
                "}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("prestamo", getPrestamoDataFetcher())
                        .dataFetcher("prestamos", getPrestamosDataFetcher()))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static DataFetcher<Map<String, Object>> getPrestamoDataFetcher() {
        return dataFetchingEnvironment -> {
            Integer id = dataFetchingEnvironment.getArgument("id_prestamo");
            if (id == null)
                return null;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(
                            "SELECT p.*, u.nombre, u.email FROM prestamos p INNER JOIN usuarios u ON p.ID_USUARIO = u.ID_USUARIO WHERE p.ID_PRESTAMO = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> prestamo = new HashMap<>();
                        prestamo.put("id_prestamo", rs.getInt("ID_PRESTAMO"));
                        prestamo.put("libro", rs.getString("libro"));
                        prestamo.put("fecha_prestamo",
                                rs.getDate("fecha_prestamo") != null ? rs.getDate("fecha_prestamo").toString() : null);

                        // Crear objeto usuario
                        Map<String, Object> user = new HashMap<>();
                        user.put("id_usuario", rs.getInt("ID_USUARIO"));
                        user.put("nombre", rs.getString("nombre"));
                        user.put("email", rs.getString("email"));

                        prestamo.put("usuario", user);
                        return prestamo;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };
    }

    private static DataFetcher<List<Map<String, Object>>> getPrestamosDataFetcher() {
        return dataFetchingEnvironment -> {
            List<Map<String, Object>> prestamos = new ArrayList<>();
            // Cruce con usuarios
            String sql = "SELECT p.*, u.nombre, u.email FROM prestamos p " +
                    "INNER JOIN usuarios u ON p.ID_USUARIO = u.ID_USUARIO";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id_prestamo", rs.getInt("ID_PRESTAMO"));
                    p.put("libro", rs.getString("libro"));
                    p.put("fecha_prestamo",
                            rs.getDate("fecha_prestamo") != null ? rs.getDate("fecha_prestamo").toString() : null);

                    // Crear objeto usdario anidado
                    Map<String, Object> user = new HashMap<>();
                    user.put("id_usuario", rs.getInt("ID_USUARIO"));
                    user.put("nombre", rs.getString("nombre"));
                    user.put("email", rs.getString("email"));

                    p.put("usuario", user);
                    prestamos.add(p);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            if (jsonObject.has("variables") && !jsonObject.get("variables").isJsonNull()) {
                variables = gson.fromJson(jsonObject.get("variables"), Map.class);
            }

            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();

            ExecutionResult executionResult = graphQL.execute(executionInput);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(executionResult.toSpecification()))
                    .build();

        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(err)).build();
        }
    }
}
