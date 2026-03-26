package org.example;

import Modelos.Usuario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import Modelos.Formulario;

import java.util.ArrayList;
import java.util.List;

import static javax.management.Query.eq;
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
            Usuario userDb = colUsuarios.find(eq("usuario", credenciales.getPassword() ) );

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

        app.get("/api/formulario", ctx -> {
            List<Formulario> lista = colFormularios.find().into(new ArrayList<Formulario>());
            ctx.json(lista);
        });

    }
}
