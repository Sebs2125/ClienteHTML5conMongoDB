package org.example;

import Modelos.Usuario;
import com.auth0.jwt.JWTVerifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import Modelos.Formulario;

import java.util.ArrayList;
import java.util.List;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class Main
{
    public static void main( String[] args )
    {
        //1- Configuración de MongoDB Atlas y ODM.
        String uri = "mongodb+srv://sebastianalmanzar05_db_user:vxjkvSmKHkSbBKOK@sebs2125.mdldtfy.mongodb.net/?appName=Sebs2125";

        CodecRegistry codigoRegistro = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .codecRegistry(codigoRegistro)
                .build();

        MongoClient mongoClient = MongoClients.create(settings);
        MongoDatabase database = mongoClient.getDatabase("encuestas_db");
        System.out.println("Conectado a MongoDB Atlas");

        //2- Configuracion del Server Web Javalin
        Javalin app = Javalin.create(config -> { //Server Estático
            config.staticFiles.add("/public", Location.CLASSPATH);
        }).start(7000);

        //Ruta de prueba
        app.get("/api/status", ctx -> ctx.result("Servidor funcionando correctamente"));
        app.get("/", ctx -> ctx.redirect("/index.html"));

        app.ws("/sincronizar", ws -> {
            ws.onMessage( ctx -> {
                String jsonRecibido = ctx.message();
                System.out.println("Datos recibidos del Worder: " + jsonRecibido );

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<Formulario> encuestas = mapper.readValue(jsonRecibido, new TypeReference<List<Formulario>>() {
                    });

                    MongoCollection<Formulario> coleccion = database.getCollection("formularios", Formulario.class);

                    if ( !encuestas.isEmpty() )
                    {
                        coleccion.insertMany(encuestas);
                        System.out.println("Encuestas guardadas en Atlas exitosamente.");
                        ctx.send("OK");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    ctx.send("ERROR");
                }

            });
        });

        MongoCollection<Usuario> colUsuarios = database.getCollection("usuarios", Usuario.class);

        if ( colUsuarios.countDocuments() == 0 )
        {
            colUsuarios.insertOne(new Usuario("admin", "1234", "ADMIN" ) );
            colUsuarios.insertOne(new Usuario("encuestador1", "1234", "ENCUESTADOR" ) );
        }

        app.post("/login", ctx -> {
            Usuario credenciales = ctx.bodyAsClass(Usuario.class);
            Usuario userDb = colUsuarios.find(eq("usuario", credenciales.getUsuario())).first();

            if ( userDb == null && userDb.getPassword().equals(credenciales.getPassword()) )
            {
                userDb.setPassword("");
                ctx.json( userDb );
            }
            else
            {
                ctx.status(401).result("Credenciales invalidas");
            }

        });

        MongoCollection<Formulario> colFormularios = database.getCollection("formularios", Formulario.class);

        app.get("/api/formularios", ctx -> {
            List<Formulario> lista = colFormularios.find().into(new ArrayList<Formulario>());
            ctx.json(lista);
        });

        //Punto #17 -> Configuración JWT:
        Algorithm algoritmoJWT = Algorithm.HMAC256("programadorWeb123");

        app.post("/api/rest/auth", ctx -> {
            Usuario credenciales = ctx.bodyAsClass(Usuario.class);
            Usuario userDB = colUsuarios.find(eq("usuario", credenciales.getUsuario())).first();

            if ( userDB != null && userDB.getPassword().equals(credenciales.getPassword()) )
            {
                String token = JWT.create()
                        .withIssuer("PUCMM")
                        .withClaim("usuario", userDB.getUsuario() )
                        .withClaim("rol", userDB.getRol() )
                        .sign( algoritmoJWT );

                ctx.json("{\"token\": \"" + token + "\"}");
            }
            else
            {
                ctx.status(401).result("Credenciales invalidas");
            }
        });

        app.before("/api/rest/formularios/*", ctx -> {
            String header = ctx.header("Authorizacion");

            if ( header == null || !header.startsWith("Bearer ") )
            {
                throw new io.javalin.http.UnauthorizedResponse("Token JWT requerido");
            }

            try {
                String token = header.substring(7);
                JWTVerifier verificador = JWT.require(algoritmoJWT).withIssuer("PUCMM").build();
                DecodedJWT jwtDecodificado = verificador.verify(token);

                ctx.attribute("usuarioJWT", jwtDecodificado.getClaim("usuario").asString());

            }
            catch (Exception e)
            {
                throw new io.javalin.http.UnauthorizedResponse("Token JWT invalido o expirado");
            }

        });

        //Punto #16:
        app.get("/api/rest/formularios/mis-registros", ctx -> {
           String usuarioLogueado = ctx.attribute("usuarioJWT");
           List<Formulario> listaUsuario = colFormularios.find(eq("usuarioRegistro", usuarioLogueado)).into(new ArrayList<>());
           ctx.json( listaUsuario );
        });

        app.post("/api/rest/formularios/crear", ctx -> {
           try {
               Formulario nuevoFormulario = ctx.bodyAsClass(Formulario.class);
               String usuarioLogueado = ctx.attribute("usuarioJWT");
               nuevoFormulario.setUsuarioRegistro(usuarioLogueado);
               colFormularios.insertOne(nuevoFormulario);
               ctx.status(201).result("Formulario creado exitosamente via REST");
           } catch (Exception e) {
               ctx.status(400).result("Error procesando el formulario: " + e.getMessage());
           }
        });

        try {
            Server grpcServer = ServerBuilder.forPort(50051)
                    .addService(new EncuestaServiceImpl(colFormularios))
                    .build()
                    .start();

            System.out.println("Servidor gRPC iniciado en el puerto 50051");

            // Mantener el servidor corriendo si la app principal se detiene
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Apagando servidor gRPC...");
                grpcServer.shutdown();
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
