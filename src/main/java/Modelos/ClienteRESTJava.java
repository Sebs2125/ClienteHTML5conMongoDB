package Modelos;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ClienteRESTJava extends JFrame
{
    private String jwtToken = "";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ClienteRESTJava() {
        setTitle("Cliente REST - Java Swing");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JButton btnLogin = new JButton("1. Login (Obtener JWT)");
        JButton btnListar = new JButton("2. Listar Encuestas");
        JTextArea txtResultado = new JTextArea();

        // Acción de Login
        btnLogin.addActionListener(e -> {
            try {
                // Ajusta el JSON y la URL a tu API
                String jsonAuth = "{\"username\":\"admin\", \"password\":\"1234\"}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7000/api/auth"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonAuth))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    // Aquí deberías extraer el token del JSON de respuesta. Simplificado para el ejemplo:
                    jwtToken = "TOKEN_EXTRAIDO_DEL_JSON";
                    txtResultado.setText("Login Exitoso. Token guardado.");
                } else {
                    txtResultado.setText("Error en login: " + response.statusCode());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Acción de Listar
        btnListar.addActionListener(e -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7000/api/encuestas"))
                        .header("Authorization", "Bearer " + jwtToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                txtResultado.setText(response.body()); // Muestra el JSON devuelto
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        JPanel panelBotones = new JPanel(new GridLayout(2, 1));
        panelBotones.add(btnLogin);
        panelBotones.add(btnListar);

        add(panelBotones, BorderLayout.NORTH);
        add(new JScrollPane(txtResultado), BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteRESTJava().setVisible(true));
    }
}
