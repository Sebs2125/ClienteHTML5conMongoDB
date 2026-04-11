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
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.Map;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Main — Punto de entrada de la aplicación
 */
public class Main {
    private static TemplateEngine templateEngine;

    public static void main(String[] args) {

        //1- MongoDB Atlas & Inicialización
        String uri = "mongodb+srv://eeeb0002_db_user:3Ch9p4xut9kpE2fB@prueba0.zgrgp7d.mongodb.net/?retryWrites=true&w=majority&appName=prueba0";

        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        MongoClient mongoClient = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(uri))
                        .codecRegistry(codecRegistry)
                        .build()
        );

        System.out.println(" Conectado a MongoDB Atlas");

        //2- Configurar Morphia (ODM-Requisito 5)
        Datastore datastore = Morphia.createDatastore(mongoClient, "encuestas_db");

        //3- Colecciones Nativas
        MongoDatabase database = mongoClient.getDatabase("encuestas_db");
        MongoCollection<Usuario> colUsuarios = database.getCollection("usuarios", Usuario.class);
        MongoCollection<Formulario> colFormularios = database.getCollection("formularios", Formulario.class);


        //4- Datos de prueba (solo si la BD está vacía)
        inicializarDatos(colUsuarios);

        //5- Jackson con soporte LocalDateTime
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        //4- Algoritmo JWT compartido
        Algorithm algoritmoJWT = Algorithm.HMAC256("programadorWeb123");

        //5- Inicializar Thymeleaf
        inicializarThymeleaf();

        //6- Configuración de Javalin
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);

            // Usar Thymeleaf como renderizador
            config.fileRenderer((file, model, ctx) -> {
                Context thymeleafCtx = new Context();

                Map<String, Object> data = new HashMap<>();
                if (model != null) {
                    data.putAll((Map<String, Object>) model);
                }

                Map<String, Object> session = new HashMap<>();
                if (ctx.sessionAttribute("usuario") != null) {
                    session.put("usuario", ctx.sessionAttribute("usuario"));
                }
                if (ctx.sessionAttribute("rol") != null) {
                    session.put("rol", ctx.sessionAttribute("rol"));
                }
                data.put("session", session);

                thymeleafCtx.setVariables(data);
                return templateEngine.process(file.replaceAll("\\.html$", ""), thymeleafCtx);
            });
        }).start(7000);

        System.out.println(" Servidor Javalin en http://localhost:7000");

        // Rutas base
        app.get("/", ctx -> ctx.redirect("/login"));
        app.get("/api/status", ctx -> ctx.result("OK"));

        //7- Registrar controladores
        new AuthControlador( datastore).registrarRutas(app);
        new EncuestaControlador( datastore, mapper).registrarRutas(app);
        new AdminControlador(colFormularios, colUsuarios, mapper).registrarRutas(app);
        new ApiRestControlador(colFormularios, colUsuarios, algoritmoJWT).registrarRutas(app);
        new WebSocketControlador(colFormularios, mapper).registrarRutas(app);

        System.out.println(" Todos los controladores registrados");

        //8- Servidor gRPC
        iniciarGRPC(colFormularios);
    }

    //9- Inicializar Thymeleaf
    private static void inicializarThymeleaf() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCacheable(false); // Desactivar caché en desarrollo
        resolver.setCharacterEncoding("UTF-8");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    //Helpers privados
    private static void inicializarDatos(MongoCollection<Usuario> colUsuarios) {
        if (colUsuarios.countDocuments() == 0) {
            colUsuarios.insertOne(new Usuario("admin", "admin", "admin@pucmm.edu.do", "admin", "ADMINISTRADOR"));
            colUsuarios.insertOne(new Usuario("Encuestador1", "encuestador1", "enc1@pucmm.edu.do", "1234", "ENCUESTADOR"));
            colUsuarios.insertOne(new Usuario("Supervisor", "supervisor", "supervisor@pucmm.edu.do", "1234", "SUPERVISOR"));
            System.out.println(" Usuarios de prueba creados (admin/admin, encuestador1/1234, supervisor/1234)");
        }
    }

    private static void iniciarGRPC(MongoCollection<Formulario> colFormularios) {
        try {
            Server grpcServer = ServerBuilder.forPort(50051)
                    .addService(new EncuestaServiceImpl(colFormularios))
                    .build()
                    .start();

            System.out.println(" Servidor gRPC en puerto 50051");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(" Apagando servidor gRPC...");
                grpcServer.shutdown();
            }));

        } catch (Exception e) {
            System.err.println(" Error al iniciar gRPC: " + e.getMessage());
        }
    }
}