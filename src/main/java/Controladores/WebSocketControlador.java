package Controladores;

import Modelos.Formulario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import io.javalin.Javalin;

import java.time.LocalDateTime;
import java.util.List;

public class WebSocketControlador
{
    private final MongoCollection<Formulario> colFormularios;
    private final ObjectMapper                mapper;

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

            // Asegurar fechaRegistro en todos los registros
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
}
