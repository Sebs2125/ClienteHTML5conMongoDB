package Controladores;

import Modelos.Formulario;
import Modelos.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import io.javalin.Javalin;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;

/**
 * EncuestaControlador
 * Maneja: nueva encuesta, mis encuestas, detalle, eliminar,
 *         mapa y el endpoint de sincronización offline /api/encuestas
 */
public class EncuestaControlador
{
    private final MongoCollection<Formulario> colFormularios;
    private final MongoCollection<Usuario>    colUsuarios;
    private final ObjectMapper                mapper;

    public EncuestaControlador(MongoCollection<Formulario> colFormularios,
                                MongoCollection<Usuario>    colUsuarios,
                                ObjectMapper                mapper)
    {
        this.colFormularios = colFormularios;
        this.colUsuarios    = colUsuarios;
        this.mapper         = mapper;
    }

    public void registrarRutas(Javalin app)
    {
        app.get("/encuestas/nueva",              this::mostrarFormulario);
        app.post("/api/encuestas",               this::guardarEncuesta);
        app.get("/mis-encuestas",                this::misEncuestas);
        app.get("/encuestas/{id}",               this::detalleEncuesta);
        app.post("/encuestas/{id}/eliminar",     this::eliminarEncuesta);
        app.get("/mapa",                         this::mostrarMapa);
    }

    // ── GET /encuestas/nueva ───────────────────────────────────────────────
    private void mostrarFormulario(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") == null) { ctx.redirect("/login"); return; }
        ctx.render("/templates/Encuesta_form.html", new HashMap<>());
    }

    // ── POST /api/encuestas (sincronización offline desde el JS) ───────────
    private void guardarEncuesta(io.javalin.http.Context ctx)
    {
        try {
            Formulario f = mapper.readValue(ctx.body(), Formulario.class);

            // Si el JS no mandó usuarioRegistro, lo tomamos de la sesión activa
            if (f.getUsuarioRegistro() == null || f.getUsuarioRegistro().isBlank()) {
                Usuario sesion = ctx.sessionAttribute("usuario");
                if (sesion != null) f.setUsuarioRegistro(sesion.getUsername());
            }
            if (f.getFechaRegistro() == null) f.setFechaRegistro(LocalDateTime.now());

            colFormularios.insertOne(f);
            ctx.status(201).json(Map.of("ok", true, "mensaje", "Encuesta guardada correctamente"));

        } catch (Exception e) {
            ctx.status(400).json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // ── GET /mis-encuestas ─────────────────────────────────────────────────
    private void misEncuestas(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") == null) { ctx.redirect("/login"); return; }
        Usuario sesion = ctx.sessionAttribute("usuario");

        List<Formulario> encuestas = colFormularios
                .find(eq("usuarioRegistro", sesion.getUsername()))
                .sort(descending("fechaRegistro"))
                .into(new ArrayList<>());

        enriquecer(encuestas);

        Map<String, Object> model = new HashMap<>();
        model.put("encuestas", encuestas);
        model.put("exito", ctx.queryParam("exito"));
        model.put("error", ctx.queryParam("error"));
        ctx.render("/templates/Me_encuesta.html", model);
    }

    // ── GET /encuestas/{id} ────────────────────────────────────────────────
    private void detalleEncuesta(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") == null) { ctx.redirect("/login"); return; }

        try {
            Formulario f = colFormularios
                    .find(eq("_id", new ObjectId(ctx.pathParam("id"))))
                    .first();

            if (f == null) { ctx.status(404).result("Encuesta no encontrada"); return; }

            enriquecer(List.of(f));

            Map<String, Object> model = new HashMap<>();
            model.put("encuesta", f);
            ctx.render("/templates/Encuesta_detalles.html", model);

        } catch (IllegalArgumentException e) {
            ctx.status(400).result("ID de encuesta inválido");
        }
    }

    // ── POST /encuestas/{id}/eliminar ──────────────────────────────────────
    private void eliminarEncuesta(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") == null) { ctx.redirect("/login"); return; }

        try {
            colFormularios.deleteOne(eq("_id", new ObjectId(ctx.pathParam("id"))));
            ctx.redirect("/mis-encuestas?exito=Encuesta eliminada correctamente");
        } catch (Exception e) {
            ctx.redirect("/mis-encuestas?error=Error al eliminar la encuesta");
        }
    }

    // ── GET /mapa ──────────────────────────────────────────────────────────
    private void mostrarMapa(io.javalin.http.Context ctx)
    {
        if (ctx.sessionAttribute("usuario") == null) { ctx.redirect("/login"); return; }

        List<Formulario> todas = colFormularios
                .find()
                .sort(descending("fechaRegistro"))
                .into(new ArrayList<>());

        enriquecer(todas);

        try {
            // Convertir a lista de Maps simples para Leaflet (evita problemas de serialización)
            List<Map<String, Object>> encuestasSimples = todas.stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",            f.getId() != null ? f.getId().toString() : "");
                m.put("nombre",        f.getNombre());
                m.put("sector",        f.getSector());
                m.put("nivelEscolar",  f.getNivelEscolar());
                m.put("latitud",       f.getLatitud());
                m.put("longitud",      f.getLongitud());
                m.put("fotoBase64",    f.getFotoBase64());
                m.put("fechaRegistro", f.getFechaRegistro() != null ? f.getFechaRegistro().toString() : "");
                return m;
            }).collect(Collectors.toList());

            String encuestasJson = mapper.writeValueAsString(encuestasSimples);

            Map<String, Object> model = new HashMap<>();
            model.put("encuestasJson", encuestasJson);
            ctx.render("/templates/mapa.html", model);

        } catch (Exception e) {
            ctx.status(500).result("Error al cargar el mapa: " + e.getMessage());
        }
    }

    // ── Helper: enriquece formularios con el objeto Usuario completo ───────
    private void enriquecer(List<Formulario> lista)
    {
        for (Formulario f : lista) {
            if (f.getUsuarioRegistro() != null) {
                Usuario u = colUsuarios.find(eq("usuario", f.getUsuarioRegistro())).first();
                if (u != null) {
                    u.setPassword("");
                    f.setUsuario(u);
                }
            }
            if (f.getFechaRegistro() == null) {
                f.setFechaRegistro(LocalDateTime.now());
            }
        }
    }
}
