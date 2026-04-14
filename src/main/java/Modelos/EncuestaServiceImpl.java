package Modelos;

import com.mongodb.client.MongoCollection;
import io.grpc.stub.StreamObserver;
import org.example.grpc.*;

import static com.mongodb.client.model.Filters.eq;

public class EncuestaServiceImpl extends EncuestaServicioGrpc.EncuestaServicioImplBase
{
    private final MongoCollection<Formulario> coleccion;

    // Recibimos la conexión a MongoDB
    public EncuestaServiceImpl(MongoCollection<Formulario> coleccion )
    {
        this.coleccion = coleccion;
    }

    @Override
    public void crearFormulario( FormularioRequest request, StreamObserver<FormularioRespuesta> responseObserver )
    {
        try
        {
            // Convertimos la petición gRPC a nuestra clase Java del Modelo
            Formulario form = new Formulario();
            form.setNombre(request.getNombre());
            form.setSector(request.getSector());
            form.setNivelEscolar(request.getNivelEscolar());
            form.setUsuarioRegistro(request.getUsuarioRegistro());
            form.setLatitud(request.getLatitud());
            form.setLongitud(request.getLongitud());
            form.setFotoBase64(request.getFotoBase64());

            // Guardamos en la base de datos
            coleccion.insertOne(form);

            // Respondemos al cliente gRPC
            FormularioRespuesta response = FormularioRespuesta.newBuilder()
                    .setExito(true)
                    .setMensaje("Formulario creado exitosamente vía gRPC")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            FormularioRespuesta response = FormularioRespuesta.newBuilder()
                    .setExito(false)
                    .setMensaje("Error: " + e.getMessage())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listarFormulariosPorUsuario(UsuarioRequest request, StreamObserver<ListaFormulariosRespuesta> responseObserver) {
        String usuario = request.getUsuario();
        ListaFormulariosRespuesta.Builder responseBuilder = ListaFormulariosRespuesta.newBuilder();

        // Buscamos en MongoDB filtrando por el usuario
        for (Formulario f : coleccion.find(eq("usuarioRegistro", usuario))) {
            FormularioData.Builder protoData = FormularioData.newBuilder()
                    .setId(f.getId() != null ? f.getId().toString() : "")
                    .setNombre(f.getNombre() != null ? f.getNombre() : "")
                    .setSector(f.getSector() != null ? f.getSector() : "")
                    .setNivelEscolar(f.getNivelEscolar() != null ? f.getNivelEscolar() : "")
                    .setUsuarioRegistro(f.getUsuarioRegistro() != null ? f.getUsuarioRegistro() : "")
                    .setLatitud(f.getLatitud())
                    .setLongitud(f.getLongitud())
                    .setFotoBase64(f.getFotoBase64() != null ? f.getFotoBase64() : "");

            responseBuilder.addFormularios(protoData);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }


}
