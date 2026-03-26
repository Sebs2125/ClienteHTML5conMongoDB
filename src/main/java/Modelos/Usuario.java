package Modelos;

import org.bson.types.ObjectId;

public class Usuario
{

    private ObjectId id;
    private String usuario;
    private String password;
    private String rol;

    public Usuario() {}

    public Usuario(String usuario, String password, String rol)
    {
        this.usuario = usuario;
        this.password = password;
        this.rol = rol;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

}
