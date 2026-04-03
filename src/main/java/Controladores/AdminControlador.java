package Controladores;

import Modelos.Formulario;
import Modelos.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import io.javalin.Javalin;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;

/**
 * AdminControlador
 * Maneja: dashboard, gestión de usuarios, todas las encuestas, exportar CSV
 * Solo accesible para roles ADMINISTRADOR y SUPERVISOR
 */
public class AdminControlador
{
    private final MongoCollection<Formulario> colFormularios;
    private final MongoCollection<Usuario>    colUsuarios;
    private final ObjectMapper                mapper;

    public AdminControlador(MongoCollection<Formulario> colFormularios,
                             MongoCollection<Usuario>    colUsuarios,
                             ObjectMapper                mapper)
    {
        this.colFormularios = colFormularios;
        this.colUsuarios    = colUsuarios;
        this.mapper         = mapper;
    }

    public void registrarRutas(Javalin app)
    {
        app.get("/admin/dashboard",  this::dashboard);
        app.get("/admin/usuarios",   this::gestionUsuarios);
        app.get("/admin/encuestas",  this::todasLasEncuestas);
        app.get("/admin/exportar",   this::exportarCSV);
    }

    // ── GET /admin/dashboard ───────────────────────────────────────────────
    private void dashboard(io.javalin.http.Context ctx)
    {
        if (!esAdmin(ctx)) return;

        try {
            Map<String, Object> model = construirModeloDashboard();
            ctx.render("dashboard.html", model);
        } catch (Exception e) {
            ctx.status(500).result("Error al cargar el dashboard: " + e.getMessage());
        }
    }

    // ── GET /admin/usuarios ────────────────────────────────────────────────
    private void gestionUsuarios(io.javalin.http.Context ctx)
    {
        if (!soloAdmin(ctx)) return;

        try {
            List<Usuario> usuarios = colUsuarios.find().into(new ArrayList<>());
            usuarios.forEach(u -> u.setPassword("")); // nunca exponer contraseñas

            Map<String, Object> model = construirModeloDashboard();
            model.put("usuarios", usuarios);
            ctx.render("usuarios.html", model);

        } catch (Exception e) {
            ctx.status(500).result("Error al cargar usuarios: " + e.getMessage());
        }
    }

    // ── GET /admin/encuestas ───────────────────────────────────────────────
    private void todasLasEncuestas(io.javalin.http.Context ctx)
    {
        if (!esAdmin(ctx)) return;

        List<Formulario> todas = colFormularios
                .find()
                .sort(descending("fechaRegistro"))
                .into(new ArrayList<>());

        enriquecer(todas);

        Map<String, Object> model = new HashMap<>();
        model.put("encuestas", todas);
        ctx.render("me_encuesta.html", model);
    }

    // ── GET /admin/exportar ────────────────────────────────────────────────
    private void exportarCSV(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") == null) { ctx.redirect("/login"); return; }

        List<Formulario> todas = colFormularios.find().into(new ArrayList<>());

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nombre,Sector,NivelEscolar,UsuarioRegistro,Latitud,Longitud,FechaRegistro\n");

        for (Formulario f : todas) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    f.getId() != null ? f.getId().toString() : "",
                    escaparCSV(f.getNombre()),
                    escaparCSV(f.getSector()),
                    escaparCSV(f.getNivelEscolar()),
                    escaparCSV(f.getUsuarioRegistro()),
                    f.getLatitud(),
                    f.getLongitud(),
                    f.getFechaRegistro() != null ? f.getFechaRegistro().toString() : ""
            ));
        }

        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", "attachment; filename=encuestas_export.csv");
        ctx.result(csv.toString());
    }

    // ── Helpers privados ───────────────────────────────────────────────────

    /**
     * Construye el model compartido para Dashboard y Usuarios
     * (stats, últimas encuestas, gráfico de niveles)
     */
    private Map<String, Object> construirModeloDashboard() throws Exception
    {
        long totalEncuestas     = colFormularios.countDocuments();
        long totalUsuarios      = colUsuarios.countDocuments();
        long totalEncuestadores = colUsuarios.find(eq("rol", "ENCUESTADOR"))
                .into(new ArrayList<>()).size();

        LocalDateTime inicioHoy = LocalDateTime.now().toLocalDate().atStartOfDay();
        long encuestasHoy = colFormularios.find()
                .into(new ArrayList<>())
                .stream()
                .filter(f -> f.getFechaRegistro() != null && f.getFechaRegistro().isAfter(inicioHoy))
                .count();

        List<Formulario> ultimas = colFormularios
                .find()
                .sort(descending("fechaRegistro"))
                .limit(10)
                .into(new ArrayList<>());
        enriquecer(ultimas);

        // Estadísticas por nivel para el gráfico de dona
        Map<String, Long> nivelStats = new LinkedHashMap<>();
        for (String nivel : List.of("BASICO", "MEDIO", "UNIVERSITARIO", "POSTGRADO", "DOCTORADO")) {
            nivelStats.put(nivel, colFormularios.countDocuments(eq("nivelEscolar", nivel)));
        }

        Map<String, Object> model = new HashMap<>();
        model.put("totalEncuestas",      totalEncuestas);
        model.put("totalUsuarios",       totalUsuarios);
        model.put("encuestasHoy",        encuestasHoy);
        model.put("totalEncuestadores",  totalEncuestadores);
        model.put("ultimasEncuestas",    ultimas);
        model.put("nivelStats",          mapper.writeValueAsString(nivelStats));
        return model;
    }

    /** Enriquece los formularios con el objeto Usuario completo */
    private void enriquecer(List<Formulario> lista)
    {
        for (Formulario f : lista) {
            if (f.getUsuarioRegistro() != null) {
                Usuario u = colUsuarios.find(eq("username", f.getUsuarioRegistro())).first();
                if (u != null) { u.setPassword(""); f.setUsuario(u); }
            }
            if (f.getFechaRegistro() == null) f.setFechaRegistro(LocalDateTime.now());
        }
    }

    /** Verifica que el usuario en sesión sea ADMINISTRADOR o SUPERVISOR */
    private boolean esAdmin(io.javalin.http.Context ctx)
    {
        Usuario sesion = ctx.sessionAttribute("usuario");
        if (sesion == null) { ctx.redirect("/login"); return false; }
        String rol = sesion.getRol();
        if (!"ADMINISTRADOR".equals(rol) && !"SUPERVISOR".equals(rol)) {
            ctx.redirect("/encuestas/nueva");
            return false;
        }
        return true;
    }

    /** Verifica que el usuario en sesión sea SOLO ADMINISTRADOR */
    private boolean soloAdmin(io.javalin.http.Context ctx)
    {
        Usuario sesion = ctx.sessionAttribute("usuario");
        if (sesion == null) { ctx.redirect("/login"); return false; }
        if (!"ADMINISTRADOR".equals(sesion.getRol())) {
            ctx.redirect("/encuestas/nueva");
            return false;
        }
        return true;
    }

    /** Escapa comas y comillas para exportación CSV */
    private String escaparCSV(String valor)
    {
        if (valor == null) return "";
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }
}
