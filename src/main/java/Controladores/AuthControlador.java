package Controladores;

import Modelos.Usuario;
import com.mongodb.client.MongoCollection;
import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * AuthControlador
 * Maneja: GET /login, POST /login, GET /logout, GET /registro, POST /registro
 */
public class AuthControlador
{
    private final MongoCollection<Usuario> colUsuarios;

    public AuthControlador(MongoCollection<Usuario> colUsuarios)
    {
        this.colUsuarios = colUsuarios;
    }

    public void registrarRutas(Javalin app)
    {
        app.get("/login",    this::mostrarLogin);
        app.post("/login",   this::procesarLogin);
        app.get("/logout",   this::logout);
        app.get("/registro", this::mostrarRegistro);
        app.post("/registro",this::procesarRegistro);
    }

    // ── GET /login ─────────────────────────────────────────────────────────
    private void mostrarLogin(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") != null) {
            ctx.redirect("/encuestas/nueva");
            return;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("error", ctx.queryParam("error"));
        model.put("exito", ctx.queryParam("exito"));
        ctx.render("login ", model);
    }

    // ── POST /login ────────────────────────────────────────────────────────
    private void procesarLogin(io.javalin.http.Context ctx)
    {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        Usuario userDb = colUsuarios.find(eq("usuario", username)).first();

        if (userDb != null && userDb.getPassword().equals(password))
        {
            userDb.setPassword(""); // no guardar contraseña en sesión
            ctx.sessionAttribute("usuario", userDb);
            ctx.sessionAttribute("rol",     userDb.getRol());

            switch (userDb.getRol()) {
                case "ADMINISTRADOR":
                case "SUPERVISOR":
                    ctx.redirect("/admin/dashboard");
                    break;
                default:
                    ctx.redirect("/encuestas/nueva");
            }
        }
        else
        {
            ctx.redirect("/login?error=Usuario o contraseña incorrectos");
        }
    }

    // ── GET /logout ────────────────────────────────────────────────────────
    private void logout(io.javalin.http.Context ctx)
    {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login?exito=Sesión cerrada correctamente");
    }

    // ── GET /registro ──────────────────────────────────────────────────────
    private void mostrarRegistro(io.javalin.http.Context ctx)
    {
        Map<String, Object> model = new HashMap<>();
        model.put("error", ctx.queryParam("error"));
        ctx.render("registro", model);
    }

    // ── POST /registro ─────────────────────────────────────────────────────
    private void procesarRegistro(io.javalin.http.Context ctx)
    {
        String nombre   = ctx.formParam("nombre");
        String username = ctx.formParam("username");
        String email    = ctx.formParam("email");
        String password = ctx.formParam("password");

        // Validar que el username no exista ya
        if (colUsuarios.find(eq("usuario", username)).first() != null) {
            ctx.redirect("/registro?error=El usuario '" + username + "' ya está en uso");
            return;
        }

        Usuario nuevo = new Usuario(nombre, username, email, password, "ENCUESTADOR");
        colUsuarios.insertOne(nuevo);
        ctx.redirect("/login?exito=Cuenta creada exitosamente. Inicia sesión.");
    }
}
