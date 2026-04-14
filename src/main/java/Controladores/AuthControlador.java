package Controladores;

import Modelos.Usuario;
import dev.morphia.Datastore;
import dev.morphia.query.filters.Filters;
import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public class AuthControlador {

    private final Datastore datastore;

    public AuthControlador(Datastore datastore) {
        this.datastore = datastore;
    }

    public void registrarRutas(Javalin app) {
        app.get("/login",    this::mostrarLogin);
        app.post("/login",   this::procesarLogin);
        app.get("/logout",   this::logout);
        app.get("/registro", this::mostrarRegistro);
        app.post("/registro",this::procesarRegistro);
        app.get("/perfil",   this::mostrarPerfil);
    }


    private void mostrarLogin(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") != null) {
            ctx.redirect("/encuestas/nueva");
            return;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("error", ctx.queryParam("error"));
        model.put("exito", ctx.queryParam("exito"));
        ctx.render("login.html", model);
    }


    private void procesarLogin(io.javalin.http.Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");


        Usuario userDb = datastore.find(Usuario.class)
                .filter(Filters.eq("username", username))
                .first();

        if (userDb != null && userDb.getPassword().equals(password) && userDb.isActivo()){

            userDb.setPassword("");
            ctx.sessionAttribute("usuario", userDb);
            ctx.sessionAttribute("rol",     userDb.getRol());

            switch (userDb.getRol()) {
                case "ADMINISTRADOR":
                    ctx.redirect("/admin/dashboard");
                    break;
                case "SUPERVISOR":
                    ctx.redirect("/supervisor/dashboard");
                    break;
                default:
                    ctx.redirect("/encuestas/nueva");
            }
        } else {
            ctx.redirect("/login?error=Usuario o contraseña incorrectos");
        }
    }


    private void logout(io.javalin.http.Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login?exito=Sesión cerrada correctamente");
    }


    private void mostrarRegistro(io.javalin.http.Context ctx) {
        Map<String, Object> model = new HashMap<>();
        model.put("error", ctx.queryParam("error"));
        ctx.render("registro.html", model);
    }


    private void procesarRegistro(io.javalin.http.Context ctx) {
        String nombre   = ctx.formParam("nombre");
        String username = ctx.formParam("username");
        String email    = ctx.formParam("email");
        String password = ctx.formParam("password");

        Usuario existente = datastore.find(Usuario.class)
                .filter(Filters.eq("username", username))
                .first();

        if (existente != null) {
            ctx.redirect("/registro?error=El usuario '" + username + "' ya está en uso");
            return;
        }

        Usuario nuevo = new Usuario(username, nombre, email, password, "ENCUESTADOR");

        datastore.save(nuevo);

        ctx.redirect("/login?exito=Cuenta creada exitosamente. Inicia sesión.");
    }

    private void mostrarPerfil(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("/login");
            return;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("error",  ctx.queryParam("error"));
        model.put("exito",  ctx.queryParam("exito"));

        ctx.render("perfil.html", model);
    }

}