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

public class UsuariosGraphQLFunction {

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
                .type("Query", builder -> builder.dataFetcher("usuario", getUsuarioDataFetcher())
                                                 .dataFetcher("usuarios", getUsuariosDataFetcher()))
                .build();
                
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        
        return GraphQL.newGraphQL(graphQLSchema).build();
    }
    
    private static DataFetcher<Map<String, Object>> getUsuarioDataFetcher() {
        return dataFetchingEnvironment -> {
            Integer id = dataFetchingEnvironment.getArgument("id_usuario");
            if(id == null) return null;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sqlUser = "SELECT ID_USUARIO, nombre, email FROM usuarios WHERE ID_USUARIO = ?";
                PreparedStatement stmt = conn.prepareStatement(sqlUser);
                stmt.setInt(1, id);
                try(ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> usuario = new HashMap<>();
                        int idU = rs.getInt("ID_USUARIO");
                        usuario.put("id_usuario", idU);
                        usuario.put("nombre", rs.getString("nombre"));
                        usuario.put("email", rs.getString("email"));
                        
                        List<Map<String, Object>> prestamosUser = new ArrayList<>();
                        String sqlP = "SELECT ID_PRESTAMO, libro, fecha_prestamo FROM prestamos WHERE ID_USUARIO = ?";
                        PreparedStatement stmtP = conn.prepareStatement(sqlP);
                        stmtP.setInt(1, idU);
                        ResultSet rsP = stmtP.executeQuery();
                        while(rsP.next()){
                            Map<String, Object> p = new HashMap<>();
                            p.put("id_prestamo", rsP.getInt("ID_PRESTAMO"));
                            p.put("libro", rsP.getString("libro"));
                            p.put("fecha_prestamo", rsP.getString("fecha_prestamo"));
                            prestamosUser.add(p);
                        }
                        usuario.put("prestamos", prestamosUser);
                        return usuario;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };
    }
    
    private static DataFetcher<List<Map<String, Object>>> getUsuariosDataFetcher() {
        return dataFetchingEnvironment -> {
            List<Map<String, Object>> usuarios = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                //Buscar usuarios
                String sqlUsers = "SELECT ID_USUARIO, nombre, email FROM usuarios";
                PreparedStatement stmtUser = conn.prepareStatement(sqlUsers);
                ResultSet rsUser = stmtUser.executeQuery();
                
                while (rsUser.next()) {
                    Map<String, Object> usuario = new HashMap<>();
                    int idU = rsUser.getInt("ID_USUARIO");
                    usuario.put("id_usuario", idU);
                    usuario.put("nombre", rsUser.getString("nombre"));
                    usuario.put("email", rsUser.getString("email"));

                    //Por cada usuario buscar sus préstamos
                    List<Map<String, Object>> prestamosUser = new ArrayList<>();
                    String sqlPrestamos = "SELECT ID_PRESTAMO, libro, fecha_prestamo FROM prestamos WHERE ID_USUARIO = ?";
                    PreparedStatement stmtP = conn.prepareStatement(sqlPrestamos);
                    stmtP.setInt(1, idU);
                    ResultSet rsP = stmtP.executeQuery();
                    while(rsP.next()){
                        Map<String, Object> p = new HashMap<>();
                        p.put("id_prestamo", rsP.getInt("ID_PRESTAMO"));
                        p.put("libro", rsP.getString("libro"));
                        p.put("fecha_prestamo", rsP.getString("fecha_prestamo"));
                        prestamosUser.add(p);
                    }
                    usuario.put("prestamos", prestamosUser);
                    usuarios.add(usuario);
                }
            } catch (Exception e) { e.printStackTrace(); }
            return usuarios;
        };
    }

    private static final GraphQL graphQL = buildGraphQL();

    @FunctionName("UsuariosGraphQLFunction")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
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
