package Controladores;

import Modelos.Formulario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class WebSocketControlador
{
    private final MongoCollection<Formulario> colFormularios;
    private final ObjectMapper                mapper;


    private static final CopyOnWriteArraySet<WsContext> updateListeners =
            new CopyOnWriteArraySet<>();

    public WebSocketControlador(MongoCollection<Formulario> colFormularios,
                                 ObjectMapper mapper)
    {
        this.colFormularios = colFormularios;
        this.mapper         = mapper;
    }

    public void registrarRutas(Javalin app)
    {
        app.ws("/sincronizar", ws -> {
            ws.onConnect(ctx -> System.out.println(" WS conectado: " + ctx.sessionId()));
            ws.onMessage(ctx -> sincronizar(ctx));
            ws.onClose(ctx   -> System.out.println(" WS cerrado: "   + ctx.sessionId()));
            ws.onError(ctx   -> System.err.println(" WS error: "      + ctx.error()));
        });
        app.ws("/update", ws ->{
            ws.onConnect(ctx -> {
                updateListeners.add(ctx);
                System.out.println(" WS updates conectado: " + ctx.sessionId()
                        + " | total listeners: " + updateListeners.size());
            });
            ws.onClose(ctx -> {
                updateListeners.remove(ctx);
                System.out.println(" WS updates cerrado: " + ctx.sessionId());
            });
            ws.onError(ctx -> {
                updateListeners.remove(ctx);
                System.err.println(" WS updates error: " + ctx.error());
            });



        });
    }

    private void sincronizar(io.javalin.websocket.WsMessageContext ctx)
    {
        String jsonRecibido = ctx.message();
        System.out.println(" WS datos recibidos (" + jsonRecibido.length() + " bytes)");

        try {
            List<Formulario> encuestas = mapper.readValue(
                    jsonRecibido,
                    new TypeReference<List<Formulario>>() {}
            );

            if (encuestas == null || encuestas.isEmpty()) {
                ctx.send("VACIO");
                return;
            }

            encuestas.forEach(f -> {
                if (f.getFechaRegistro() == null) f.setFechaRegistro(LocalDateTime.now());
            });

            colFormularios.insertMany(encuestas);
            System.out.println(" B" + encuestas.size() + " encuesta(s) guardadas via WS");
            ctx.send("OK");

        } catch (Exception e) {
            System.err.println(" Error WS sincronización: " + e.getMessage());
            ctx.send("ERROR");
        }
    }
    private void broadcastNuevosRegistros(List<Formulario> encuestas) {
        if (updateListeners.isEmpty()) return;

        try {

            List<Map<String, Object>> records = new ArrayList<>();
            for (Formulario f : encuestas) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",           f.getId() != null ? f.getId().toString() : "");
                m.put("nombre",       f.getNombre()       != null ? f.getNombre()       : "");
                m.put("sector",       f.getSector()       != null ? f.getSector()       : "");
                m.put("nivelEscolar", f.getNivelEscolar() != null ? f.getNivelEscolar() : "");
                m.put("latitud",      f.getLatitud());
                m.put("longitud",     f.getLongitud());
                m.put("fechaRegistro",
                        f.getFechaRegistro() != null ? f.getFechaRegistro().toString() : "");
                records.add(m);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type",    "NEW_RECORDS");
            payload.put("count",   encuestas.size());
            payload.put("records", records);

            String broadcastJson = mapper.writeValueAsString(payload);


            Set<WsContext> cerrados = new HashSet<>();
            for (WsContext listener : updateListeners) {
                try {
                    listener.send(broadcastJson);
                } catch (Exception e) {
                    cerrados.add(listener);
                }
            }
            updateListeners.removeAll(cerrados);

            System.out.println(" Broadcast enviado a "
                    + (updateListeners.size()) + " cliente(s) en tiempo real");

        } catch (Exception e) {
            System.err.println(" Error en broadcast: " + e.getMessage());
        }
    }
}
