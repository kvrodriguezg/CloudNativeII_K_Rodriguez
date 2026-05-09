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

public class UsuariosGraphQLFunction {

    private static final Logger LOGGER = Logger.getLogger(UsuariosGraphQLFunction.class.getName());

    private static final String DB_URL      = System.getenv("ORACLE_DB_URL");
    private static final String DB_USER     = System.getenv("ORACLE_DB_USER");
    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD");
    private static final Gson gson = new Gson();

    // GraphQL / map key constants
    private static final String FIELD_ID_USUARIO = "id_usuario";
    private static final String FIELD_NOMBRE     = "nombre";
    private static final String FIELD_EMAIL      = "email";
    private static final String FIELD_ID_PRESTAMO = "id_prestamo";
    private static final String FIELD_LIBRO      = "libro";
    private static final String FIELD_FECHA      = "fecha_prestamo";
    private static final String FIELD_PRESTAMOS  = "prestamos";
    private static final String FIELD_ERROR      = "error";
    private static final String GRAPHQL_VARIABLES = "variables";

    // DB column constants
    private static final String COL_ID_USUARIO  = "ID_USUARIO";
    private static final String COL_ID_PRESTAMO = "ID_PRESTAMO";

    // HTTP header/value constants
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String MIME_JSON           = "application/json";

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

        //Crear objeto TypeDefinitionRegistry
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        //Crear objeto RuntimeWiring, cuando alguien pida usuario(s) ejecutar la función que le corresponde
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("usuario", getUsuarioDataFetcher())
                        .dataFetcher("usuarios", getUsuariosDataFetcher()))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();

        //Crear objeto GraphQL — se une el esquema y el wiring
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    //Función datafetcher que obtiene un usuario por id
    private static DataFetcher<Map<String, Object>> getUsuarioDataFetcher() {
        return dataFetchingEnvironment -> {
            Integer id = dataFetchingEnvironment.getArgument(FIELD_ID_USUARIO);
            if (id == null) return null;

            //Conexión a la base de datos
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT ID_USUARIO, nombre, email FROM usuarios WHERE ID_USUARIO = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> usuario = new HashMap<>();
                        int idU = rs.getInt(COL_ID_USUARIO);
                        usuario.put(FIELD_ID_USUARIO, idU);
                        usuario.put(FIELD_NOMBRE, rs.getString(FIELD_NOMBRE));
                        usuario.put(FIELD_EMAIL, rs.getString(FIELD_EMAIL));

                        //Obtener los préstamos del usuario
                        List<Map<String, Object>> prestamosUser = new ArrayList<>();
                        try (PreparedStatement stmtP = conn.prepareStatement(
                                "SELECT ID_PRESTAMO, libro, fecha_prestamo FROM prestamos WHERE ID_USUARIO = ?")) {
                            stmtP.setInt(1, idU);
                            try (ResultSet rsP = stmtP.executeQuery()) {
                                while (rsP.next()) {
                                    Map<String, Object> p = new HashMap<>();
                                    p.put(FIELD_ID_PRESTAMO, rsP.getInt(COL_ID_PRESTAMO));
                                    p.put(FIELD_LIBRO, rsP.getString(FIELD_LIBRO));
                                    p.put(FIELD_FECHA, rsP.getString(FIELD_FECHA));
                                    prestamosUser.add(p);
                                }
                            }
                        }
                        usuario.put(FIELD_PRESTAMOS, prestamosUser);
                        return usuario;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al obtener usuario", e);
            }
            return null;
        };
    }

    private static DataFetcher<List<Map<String, Object>>> getUsuariosDataFetcher() {
        return dataFetchingEnvironment -> {
            List<Map<String, Object>> usuarios = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmtUser = conn.prepareStatement(
                         "SELECT ID_USUARIO, nombre, email FROM usuarios");
                 ResultSet rsUser = stmtUser.executeQuery()) {

                while (rsUser.next()) {
                    Map<String, Object> usuario = new HashMap<>();
                    int idU = rsUser.getInt(COL_ID_USUARIO);
                    usuario.put(FIELD_ID_USUARIO, idU);
                    usuario.put(FIELD_NOMBRE, rsUser.getString(FIELD_NOMBRE));
                    usuario.put(FIELD_EMAIL, rsUser.getString(FIELD_EMAIL));

                    //Por cada usuario buscar sus préstamos
                    List<Map<String, Object>> prestamosUser = new ArrayList<>();
                    try (PreparedStatement stmtP = conn.prepareStatement(
                            "SELECT ID_PRESTAMO, libro, fecha_prestamo FROM prestamos WHERE ID_USUARIO = ?")) {
                        stmtP.setInt(1, idU);
                        try (ResultSet rsP = stmtP.executeQuery()) {
                            while (rsP.next()) {
                                Map<String, Object> p = new HashMap<>();
                                p.put(FIELD_ID_PRESTAMO, rsP.getInt(COL_ID_PRESTAMO));
                                p.put(FIELD_LIBRO, rsP.getString(FIELD_LIBRO));
                                p.put(FIELD_FECHA, rsP.getString(FIELD_FECHA));
                                prestamosUser.add(p);
                            }
                        }
                    }
                    usuario.put(FIELD_PRESTAMOS, prestamosUser);
                    usuarios.add(usuario);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al obtener usuarios", e);
            }
            return usuarios;
        };
    }

    private static final GraphQL graphQL = buildGraphQL();

    @FunctionName("UsuariosGraphQLFunction")
    //Punto de entrada
    public HttpResponseMessage run(
            //Responde al post
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        try {
            //Toma el request y extrae el query
            String body = request.getBody().orElse("");
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);
            String query = jsonObject.get("query").getAsString();

            Map<String, Object> variables = new HashMap<>();
            if (jsonObject.has(GRAPHQL_VARIABLES) && !jsonObject.get(GRAPHQL_VARIABLES).isJsonNull()) {
                variables = gson.fromJson(jsonObject.get(GRAPHQL_VARIABLES),
                        new TypeToken<Map<String, Object>>(){}.getType());
            }

            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();

            //Ejecuta el query
            ExecutionResult executionResult = graphQL.execute(executionInput);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header(HEADER_CONTENT_TYPE, MIME_JSON)
                    .body(gson.toJson(executionResult.toSpecification()))
                    .build();

        } catch (Exception e) {
            //En caso de error
            LOGGER.log(Level.SEVERE, "Error en UsuariosGraphQLFunction", e);
            Map<String, String> err = new HashMap<>();
            err.put(FIELD_ERROR, e.getMessage() != null ? e.getMessage() : e.toString());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HEADER_CONTENT_TYPE, MIME_JSON)
                    .body(gson.toJson(err)).build();
        }
    }
}
