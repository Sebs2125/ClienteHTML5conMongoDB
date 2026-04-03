package Controladores;

import Modelos.Formulario;
import Modelos.Usuario;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mongodb.client.MongoCollection;
import io.javalin.Javalin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * ApiRestControlador
 * Maneja los endpoints REST protegidos con JWT:
 *   POST /api/rest/auth                        → obtener token
 *   GET  /api/rest/formularios/mis-registros   → formularios del usuario
 *   POST /api/rest/formularios/crear           → crear formulario
 */
public class ApiRestControlador
{
    private final MongoCollection<Formulario> colFormularios;
    private final MongoCollection<Usuario>    colUsuarios;
    private final Algorithm                   algoritmoJWT;

    public ApiRestControlador(MongoCollection<Formulario> colFormularios,
                               MongoCollection<Usuario>    colUsuarios,
                               Algorithm                   algoritmoJWT)
    {
        this.colFormularios = colFormularios;
        this.colUsuarios    = colUsuarios;
        this.algoritmoJWT   = algoritmoJWT;
    }

    public void registrarRutas(Javalin app)
    {
        // Ruta pública: obtener token JWT
        app.post("/api/rest/auth", this::autenticar);

        // Middleware: protege todas las rutas /api/rest/formularios/*
        app.before("/api/rest/formularios/*", this::validarJWT);

        // Rutas protegidas
        app.get("/api/rest/formularios/mis-registros", this::misRegistros);
        app.post("/api/rest/formularios/crear",        this::crearFormulario);
    }

    // ── POST /api/rest/auth ────────────────────────────────────────────────
    private void autenticar(io.javalin.http.Context ctx)
    {
        try {
            Usuario credenciales = ctx.bodyAsClass(Usuario.class);
            Usuario userDB = colUsuarios.find(eq("username", credenciales.getUsername())).first();

            if (userDB != null && userDB.getPassword().equals(credenciales.getPassword()))
            {
                String token = JWT.create()
                        .withIssuer("PUCMM")
                        .withClaim("username", userDB.getUsername())
                        .withClaim("rol",     userDB.getRol())
                        .sign(algoritmoJWT);

                ctx.json(Map.of("token", token, "rol", userDB.getRol()));
            }
            else
            {
                ctx.status(401).json(Map.of("error", "Credenciales inválidas"));
            }
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Petición inválida: " + e.getMessage()));
        }
    }

    // ── Middleware JWT ─────────────────────────────────────────────────────
    private void validarJWT(io.javalin.http.Context ctx)
    {
        String header = ctx.header("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new io.javalin.http.UnauthorizedResponse("Token JWT requerido en el header Authorization");
        }

        try {
            String token = header.substring(7);
            JWTVerifier verificador = JWT.require(algoritmoJWT).withIssuer("PUCMM").build();
            DecodedJWT  jwt         = verificador.verify(token);

            // Guardar el usuario extraído del token para usarlo en los handlers
            ctx.attribute("usuarioJWT", jwt.getClaim("usuario").asString());
            ctx.attribute("rolJWT",     jwt.getClaim("rol").asString());

        } catch (Exception e) {
            throw new io.javalin.http.UnauthorizedResponse("Token JWT inválido o expirado");
        }
    }

    // ── GET /api/rest/formularios/mis-registros ────────────────────────────
    private void misRegistros(io.javalin.http.Context ctx)
    {
        String usuarioLogueado = ctx.attribute("usuarioJWT");
        List<Formulario> lista = colFormularios
                .find(eq("usuarioRegistro", usuarioLogueado))
                .into(new ArrayList<>());
        ctx.json(lista);
    }

    // ── POST /api/rest/formularios/crear ───────────────────────────────────
    private void crearFormulario(io.javalin.http.Context ctx)
    {
        try {
            Formulario f = ctx.bodyAsClass(Formulario.class);
            f.setUsuarioRegistro(ctx.attribute("usuarioJWT"));
            if (f.getFechaRegistro() == null) f.setFechaRegistro(LocalDateTime.now());

            colFormularios.insertOne(f);
            ctx.status(201).json(Map.of("ok", true, "mensaje", "Formulario creado exitosamente via REST"));

        } catch (Exception e) {
            ctx.status(400).json(Map.of("ok", false, "error", "Error procesando formulario: " + e.getMessage()));
        }
    }
}
