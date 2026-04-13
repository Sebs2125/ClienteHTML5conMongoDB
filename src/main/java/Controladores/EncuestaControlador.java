package Controladores;

import Modelos.Formulario;
import Modelos.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.morphia.Datastore;
import dev.morphia.query.Sort;
import dev.morphia.query.filters.Filters;
import io.javalin.Javalin;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class EncuestaControlador {
    private final Datastore datastore;
    private final ObjectMapper mapper;

    //Morphia (Requerimiento #5)
    public EncuestaControlador(Datastore datastore, ObjectMapper mapper) {
        this.datastore = datastore;
        this.mapper = mapper;
    }

    public void registrarRutas(Javalin app) {
        app.get("/encuestas/nueva",          this::mostrarFormulario);
        app.post("/api/encuestas",           this::guardarEncuesta);
        app.get("/mis-encuestas",            this::misEncuestas);
        app.get("/encuestas/{id}",           this::detalleEncuesta);
        app.get("/encuestas/{id}/editar",    this::mostrarEdicion);
        app.post("/encuestas/{id}/editar",   this::procesarEdicion);
        app.post("/encuestas/{id}/eliminar", this::eliminarEncuesta);
        app.get("/mapa",                     this::mostrarMapa);
    }
    //GET
    private void mostrarFormulario(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("login");
            return;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("usuario", ctx.sessionAttribute("usuario"));
        ctx.render("encuesta_form.html", model);
    }

    //POST
    private void guardarEncuesta(io.javalin.http.Context ctx) {
        try {

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> body = mapper.readValue(ctx.body(), java.util.Map.class);

            Formulario f = new Formulario();
            f.setNombre((String) body.get("nombre"));
            f.setSector((String) body.get("sector"));
            f.setNivelEscolar((String) body.get("nivelEscolar"));

            String usuReg = (String) body.get("usuarioRegistro");
            if (usuReg == null || usuReg.isBlank()) {
                usuReg = (String) body.get("usuarioId");
            }
            if (usuReg == null || usuReg.isBlank()) {
                Usuario sesion = ctx.sessionAttribute("usuario");
                if (sesion != null) usuReg = sesion.getUsername();
            }
            f.setUsuarioRegistro(usuReg);

            Object lat = body.get("latitud");
            Object lng = body.get("longitud");
            if (lat != null && !lat.toString().isBlank()) {
                f.setLatitud(Double.parseDouble(lat.toString()));
            } else {
                f.setLatitud(0.0);
            }

            if (lng != null && !lng.toString().isBlank()) {
                f.setLongitud(Double.parseDouble(lng.toString()));
            } else {
                f.setLongitud(0.0);
            }

            f.setFotoBase64((String) body.get("fotoBase64"));

            f.setFechaRegistro(java.time.LocalDateTime.now());


            datastore.save(f);
            ctx.status(201).json(Map.of("ok", true, "mensaje", "Encuesta guardada correctamente"));

        } catch (Exception e) {
            System.err.println("Error guardarEncuesta " + e.getMessage());
            ctx.status(400).json(java.util.Map.of("ok", false, "error", e.getMessage()));
        }
    }

    //GET
    private void misEncuestas(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("login");
            return;
        }
        Usuario sesion = ctx.sessionAttribute("usuario");

        // Búsqueda con Morphia
        List<Formulario> encuestas = datastore.find(Formulario.class)
                .filter(Filters.eq("usuarioRegistro", sesion.getUsername()))
                .iterator(new dev.morphia.query.FindOptions().sort(Sort.descending("fechaRegistro")))
                .toList();

        enriquecer(encuestas);

        Map<String, Object> model = new HashMap<>();
        model.put("encuestas", encuestas);
        model.put("exito", ctx.queryParam("exito"));
        model.put("error", ctx.queryParam("error"));
        ctx.render("me_encuesta.html", model);
    }

    //GET
    private void detalleEncuesta(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("login");
            return;
        }

        try {
            // Búsqueda por ID en Morphia
            Formulario f = datastore.find(Formulario.class)
                    .filter(Filters.eq("_id", new ObjectId(ctx.pathParam("id"))))
                    .first();

            if (f == null) {
                ctx.status(404).result("Encuesta no encontrada");
                return;
            }

            enriquecer(List.of(f));

            Map<String, Object> model = new HashMap<>();
            model.put("encuesta", f);
            ctx.render("encuesta_detalles.html", model);

        } catch (IllegalArgumentException e) {
            ctx.status(400).result("ID de encuesta inválido");
        }
    }

    //POST
    private void eliminarEncuesta(io.javalin.http.Context ctx) {

        String rol = "";
        Usuario sesion = ctx.sessionAttribute("usuario");
        if (sesion != null) rol = sesion.getRol();

        if ("ADMINISTRADOR".equals(rol) || "SUPERVISOR".equals(rol)) {
            ctx.redirect("/admin/encuestas?exito=Encuesta eliminada correctamente");
        } else {
            ctx.redirect("/mis-encuestas?exito=Encuesta eliminada correctamente");
        }


        try {
            // Eliminar con Morphia
            datastore.find(Formulario.class)
                    .filter(Filters.eq("_id", new ObjectId(ctx.pathParam("id"))))
                    .delete();

            ctx.redirect("/mis-encuestas?exito=Encuesta eliminada correctamente");
        } catch (Exception e) {
            ctx.redirect("/mis-encuestas?error=Error al eliminar la encuesta");
        }
    }

    //GET
    private void mostrarMapa(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("login");
            return;
        }

        // Búsqueda de todas las encuestas con Morphia
        List<Formulario> todas = datastore.find(Formulario.class)
                .iterator(new dev.morphia.query.FindOptions().sort(Sort.descending("fechaRegistro")))
                .toList();

        enriquecer(todas);

        try {
            List<Map<String, Object>> encuestasSimples = todas.stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", f.getId() != null ? f.getId().toString() : "");
                m.put("nombre", f.getNombre());
                m.put("sector", f.getSector());
                m.put("nivelEscolar", f.getNivelEscolar());
                m.put("latitud", f.getLatitud());
                m.put("longitud", f.getLongitud());
                m.put("fotoBase64", f.getFotoBase64());
                m.put("fechaRegistro", f.getFechaRegistro() != null ? f.getFechaRegistro().toString() : "");
                return m;
            }).collect(Collectors.toList());

            String encuestasJson = mapper.writeValueAsString(encuestasSimples);

            Map<String, Object> model = new HashMap<>();
            model.put("encuestasJson", encuestasJson);
            ctx.render("mapa.html", model);

        } catch (Exception e) {
            ctx.status(500).result("Error al cargar el mapa: " + e.getMessage());
        }
    }

    //Helper, es un objeto que ayuda a los formularios a buscar el usuario eficientemente
    private void enriquecer(List<Formulario> lista) {
        for (Formulario f : lista) {
            if (f.getUsuarioRegistro() != null) {
                // Búsqueda del usuario con Morphia
                Usuario u = datastore.find(Usuario.class)
                        .filter(Filters.eq("username", f.getUsuarioRegistro()))
                        .first();

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

    private void mostrarFormularioEdicion(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("/login");
            return;
        }
        try {
            Formulario f = datastore.find(Formulario.class)
                    .filter(Filters.eq("_id", new ObjectId(ctx.pathParam("id"))))
                    .first();
            if (f == null) {
                ctx.status(404).result("Encuesta no encontrada");
                return;
            }

            Map<String, Object> model = new HashMap<>();
            model.put("encuesta", f);
            model.put("editando", true);
            ctx.render("encuesta_editar.html", model);

        } catch (Exception e) {
            ctx.status(400).result("ID invalido");


        }

    }

    private void procesarEdicion(io.javalin.http.Context ctx) {
        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("/login");
            return;
        }

        try {
            ObjectId objectId = new ObjectId(ctx.pathParam("id"));
            Formulario existing = datastore.find(Formulario.class)
                    .filter(Filters.eq("_id", objectId))
                    .first();
            if (existing == null) {
                ctx.status(404).result("Encuesta no encontrada");
                return;
            }

            existing.setNombre(ctx.formParam("nombre"));
            existing.setSector(ctx.formParam("sector"));
            existing.setNivelEscolar(ctx.formParam("nivelEscolar"));

            String nuevaFoto = ctx.formParam("fotoBase64");
            if (nuevaFoto != null && !nuevaFoto.isBlank()) {
                existing.setFotoBase64(nuevaFoto);
            }

            datastore.save(existing);
            ctx.redirect("/mis-encuestas?exito=Encuesta actualizada correctamente");
        } catch (Exception e) {
            ctx.redirect("/mis-encuestas?error=Error al actualizar: " + e.getMessage());
        }


    }

    private void mostrarEdicion(io.javalin.http.Context ctx) {

        if (ctx.sessionAttribute("usuario") == null) {
            ctx.redirect("/login");
            return;
        }
        try {
            Formulario f = datastore.find(Formulario.class)
                    .filter(Filters.eq("_id", new ObjectId(ctx.pathParam("id"))))
                    .first();
            if (f == null) {
                ctx.status(404).result("Encuesta no encontrada");
                return;
            }

            Map<String, Object> model = new HashMap<>();
            model.put("encuesta", f);
            model.put("editando", true);
            ctx.render("encuesta_form.html", model);
        } catch (Exception e) {
            ctx.redirect("/mis-encuestas?error=Error al cargar la encuesta");
        }
    }
}









