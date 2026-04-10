package Modelos;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;

@Entity("formularios")
public class Formulario
{

    @Id
    private ObjectId id;
    private String nombre;
    private String sector;
    private String nivelEscolar;
    private String usuarioRegistro;
    private double latitud;
    private double longitud;
    private String fotoBase64;
    private LocalDateTime fechaRegistro;

    // Campo transitorio: NO se guarda en MongoDB, se llena en Java
    @BsonIgnore
    private Usuario usuario;

    public Formulario() {}

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getNivelEscolar() { return nivelEscolar; }
    public void setNivelEscolar(String nivelEscolar) { this.nivelEscolar = nivelEscolar; }

    public String getUsuarioRegistro() { return usuarioRegistro; }
    public void setUsuarioRegistro(String usuarioRegistro) { this.usuarioRegistro = usuarioRegistro; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String fotoBase64) { this.fotoBase64 = fotoBase64; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    @BsonIgnore
    public Usuario getUsuario() { return usuario; }
    @BsonIgnore
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}