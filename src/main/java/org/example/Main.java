package org.example;

import Controladores.*;
import Modelos.EncuestaServiceImpl;
import Modelos.Formulario;
import Modelos.Usuario;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.ArrayList;

import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
/**
 * Main — Punto de entrada de la aplicación
 *
 * Responsabilidades:
 *  1. Conectar a MongoDB Atlas
 *  2. Configurar Jackson (JSON)
 *  3. Crear el servidor Javalin
 *  4. Instanciar e inyectar los controladores
 *  5. Arrancar el servidor gRPC
 *  6. Crear datos de prueba si la BD está vacía
 */
public class Main
{
    public static void main(String[] args)
    {
        // ── 1. MongoDB Atlas ───────────────────────────────────────────────
        String uri = "mongodb+srv://eeeb0002_db_user:3Ch9p4xut9kpE2fB@prueba0.zgrgp7d.mongodb.net/?retryWrites=true&w=majority&appName=prueba0";

        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        MongoClient   mongoClient = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(uri))
                        .codecRegistry(codecRegistry)
                        .build()
        );

        MongoDatabase database = mongoClient.getDatabase("encuestas_db");
        System.out.println("✅ Conectado a MongoDB Atlas");

        MongoCollection<Usuario>    colUsuarios    = database.getCollection("usuarios",    Usuario.class);
        MongoCollection<Formulario> colFormularios = database.getCollection("formularios", Formulario.class);

        // ── 2. Datos de prueba (solo si la BD está vacía) ──────────────────
        inicializarDatos(colUsuarios);

        // ── 3. Jackson con soporte LocalDateTime ───────────────────────────
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ── 4. Algoritmo JWT compartido ────────────────────────────────────
        Algorithm algoritmoJWT = Algorithm.HMAC256("programadorWeb123");

        // ── 5. Javalin ─────────────────────────────────────────────────────
        Javalin app = Javalin.create(config ->
                config.staticFiles.add("/public", Location.CLASSPATH);
        // Asegura que Thymeleaf busque en /templates
        config.fileRenderer(new JavalinThymeleaf());

        ).start(7000);

        System.out.println("✅ Servidor Javalin en http://localhost:7000");

        // Ruta raíz
        app.get("/", ctx -> ctx.redirect("/login"));
        app.get("/api/status", ctx -> ctx.result("OK"));

        // ── 6. Registrar controladores ─────────────────────────────────────
        new AuthControlador(colUsuarios)
                .registrarRutas(app);

        new EncuestaControlador(colFormularios, colUsuarios, mapper)
                .registrarRutas(app);

        new AdminControlador(colFormularios, colUsuarios, mapper)
                .registrarRutas(app);

        new ApiRestControlador(colFormularios, colUsuarios, algoritmoJWT)
                .registrarRutas(app);

        new WebSocketControlador(colFormularios, mapper)
                .registrarRutas(app);

        System.out.println("✅ Todos los controladores registrados");

        // ── 7. Servidor gRPC ───────────────────────────────────────────────
        iniciarGRPC(colFormularios);
    }

    // ── Helpers privados ───────────────────────────────────────────────────

    private static void inicializarDatos(MongoCollection<Usuario> colUsuarios)
    {
        if (colUsuarios.countDocuments() == 0) {
            colUsuarios.insertOne(new Usuario("Administrador",  "admin",        "admin@pucmm.edu.do",       "1234", "ADMINISTRADOR"));
            colUsuarios.insertOne(new Usuario("Encuestador 1",  "encuestador1", "enc1@pucmm.edu.do",        "1234", "ENCUESTADOR"));
            colUsuarios.insertOne(new Usuario("Supervisor",     "supervisor",   "supervisor@pucmm.edu.do",  "1234", "SUPERVISOR"));
            System.out.println("✅ Usuarios de prueba creados (admin/1234, encuestador1/1234, supervisor/1234)");
        }
    }

    private static void iniciarGRPC(MongoCollection<Formulario> colFormularios)
    {
        try {
            Server grpcServer = ServerBuilder.forPort(50051)
                    .addService(new EncuestaServiceImpl(colFormularios))
                    .build()
                    .start();

            System.out.println("✅ Servidor gRPC en puerto 50051");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 Apagando servidor gRPC...");
                grpcServer.shutdown();
            }));

        } catch (Exception e) {
            System.err.println("❌ Error al iniciar gRPC: " + e.getMessage());
        }
    }
}