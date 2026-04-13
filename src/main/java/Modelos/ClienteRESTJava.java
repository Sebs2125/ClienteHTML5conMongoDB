package Modelos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

//Punto #18
public class ClienteRESTJava extends JFrame
{
    private String jwtToken = "";

    private final HttpClient   httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper     = new ObjectMapper();

    // Conexión
    private JTextField txtHost;
    private JTextField txtPuerto;
    private JLabel     lblEstadoConexion;

    // Login
    private JTextField     txtUsername;
    private JPasswordField txtPassword;
    private JLabel         lblTokenPreview;

    // Crear formulario
    private JTextField  txtNombre;
    private JTextField  txtSector;
    private JComboBox<String> cmbNivel;
    private JTextArea   txtRespuestaCrear;

    // Listar formularios
    private JTable            tablaFormularios;
    private DefaultTableModel modeloTabla;
    private JLabel            lblTotalRegistros;

    public ClienteRESTJava()
    {
        setTitle("Cliente REST — Encuestas PUCMM (JWT)");
        setSize(900, 640);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        aplicarEstilo();

        add(crearPanelConexion(), BorderLayout.NORTH);
        add(crearTabs(),          BorderLayout.CENTER);
        add(crearStatusBar(),     BorderLayout.SOUTH);
    }

    // ── Apariencia ──────────────────────────────────────────────────────────
    private void aplicarEstilo()
    {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        getContentPane().setBackground(new Color(0xFAF6F0));
    }

    // ── Panel de conexión ────────────────────────────────────────────────────
    private JPanel crearPanelConexion()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBackground(new Color(0x2D2A26));
        panel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel titulo = new JLabel("  Encuestas PUCMM — API REST");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 14));
        panel.add(titulo);

        panel.add(Box.createHorizontalStrut(20));
        panel.add(labelBlanco("Host:"));
        txtHost = new JTextField("localhost", 10);
        panel.add(txtHost);

        panel.add(labelBlanco("Puerto:"));
        txtPuerto = new JTextField("7000", 5);
        panel.add(txtPuerto);

        lblEstadoConexion = new JLabel("● Sin autenticar");
        lblEstadoConexion.setForeground(new Color(0xBF9870));
        lblEstadoConexion.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(lblEstadoConexion);

        return panel;
    }

    // ── Tabs principales ─────────────────────────────────────────────────────
    private JTabbedPane crearTabs()
    {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tabs.addTab("🔑  Autenticación",    crearTabLogin());
        tabs.addTab("📋  Mis Formularios",  crearTabListar());
        tabs.addTab("➕  Crear Formulario", crearTabCrear());
        return tabs;
    }

    // ── Tab Login ────────────────────────────────────────────────────────────
    private JPanel crearTabLogin()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(0xFAF6F0));
        panel.setBorder(new EmptyBorder(20, 40, 20, 40));
        GridBagConstraints g = new GridBagConstraints();
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.insets  = new Insets(6, 6, 6, 6);
        g.weightx = 1;

        // Título
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        JLabel titulo = new JLabel("Iniciar Sesión — Obtener Token JWT");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        titulo.setForeground(new Color(0x2D2A26));
        panel.add(titulo, g);

        g.gridy++; g.gridwidth = 1;
        panel.add(new JLabel("Usuario:"), g);
        g.gridx = 1;
        txtUsername = new JTextField("admin", 20);
        panel.add(txtUsername, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Contraseña:"), g);
        g.gridx = 1;
        txtPassword = new JPasswordField("admin", 20);
        panel.add(txtPassword, g);

        g.gridx = 0; g.gridy++; g.gridwidth = 2;
        JButton btnLogin = botonPrimario("Autenticar y Obtener JWT");
        btnLogin.addActionListener(e -> autenticar());
        panel.add(btnLogin, g);

        g.gridy++;
        JLabel lblTit = new JLabel("Token JWT obtenido:");
        lblTit.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(lblTit, g);

        g.gridy++;
        lblTokenPreview = new JLabel("—");
        lblTokenPreview.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblTokenPreview.setForeground(new Color(0x7A8C5E));
        lblTokenPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE8D5C0)),
                new EmptyBorder(8, 10, 8, 10)));
        lblTokenPreview.setBackground(new Color(0xF5EBE0));
        lblTokenPreview.setOpaque(true);
        panel.add(lblTokenPreview, g);

        return panel;
    }

    // ── Tab Listar ───────────────────────────────────────────────────────────
    private JPanel crearTabListar()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(0xFAF6F0));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Barra superior
        JPanel barraTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        barraTop.setBackground(new Color(0xFAF6F0));
        JButton btnListar = botonPrimario("Listar mis formularios");
        btnListar.addActionListener(e -> listarFormularios());
        barraTop.add(btnListar);
        lblTotalRegistros = new JLabel("Total: 0");
        lblTotalRegistros.setForeground(new Color(0x6B6258));
        barraTop.add(lblTotalRegistros);

        // Tabla
        String[] columnas = { "ID", "Nombre", "Sector", "Nivel Escolar" };
        modeloTabla = new DefaultTableModel(columnas, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaFormularios = new JTable(modeloTabla);
        tablaFormularios.setRowHeight(26);
        tablaFormularios.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tablaFormularios.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        tablaFormularios.setSelectionBackground(new Color(0xF5EBE0));

        JScrollPane scroll = new JScrollPane(tablaFormularios);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xE8D5C0)));

        panel.add(barraTop, BorderLayout.NORTH);
        panel.add(scroll,   BorderLayout.CENTER);
        return panel;
    }

    // ── Tab Crear ────────────────────────────────────────────────────────────
    private JPanel crearTabCrear()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(0xFAF6F0));
        panel.setBorder(new EmptyBorder(20, 40, 20, 40));
        GridBagConstraints g = new GridBagConstraints();
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.insets  = new Insets(6, 6, 6, 6);
        g.weightx = 1;

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        JLabel titulo = new JLabel("Crear Formulario vía REST (JWT)");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        titulo.setForeground(new Color(0x2D2A26));
        panel.add(titulo, g);

        g.gridy++; g.gridwidth = 1;
        panel.add(new JLabel("Nombre encuestado:"), g);
        g.gridx = 1;
        txtNombre = new JTextField(22);
        panel.add(txtNombre, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Sector:"), g);
        g.gridx = 1;
        txtSector = new JTextField(22);
        panel.add(txtSector, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Nivel Escolar:"), g);
        g.gridx = 1;
        cmbNivel = new JComboBox<>(new String[]{ "BASICO", "MEDIO", "UNIVERSITARIO", "POSTGRADO", "DOCTORADO" });
        panel.add(cmbNivel, g);

        g.gridx = 0; g.gridy++; g.gridwidth = 2;
        JButton btnCrear = botonPrimario("Crear Formulario");
        btnCrear.addActionListener(e -> crearFormulario());
        panel.add(btnCrear, g);

        g.gridy++;
        JLabel lblRes = new JLabel("Respuesta del servidor:");
        lblRes.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(lblRes, g);

        g.gridy++;
        txtRespuestaCrear = new JTextArea(5, 40);
        txtRespuestaCrear.setEditable(false);
        txtRespuestaCrear.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtRespuestaCrear.setBackground(new Color(0xF5EBE0));
        txtRespuestaCrear.setBorder(new EmptyBorder(8, 10, 8, 10));
        panel.add(new JScrollPane(txtRespuestaCrear), g);

        return panel;
    }

    private JPanel crearStatusBar()
    {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        bar.setBackground(new Color(0xE8D5C0));
        JLabel lbl = new JLabel("Encuestas PUCMM — Cliente REST con autenticación JWT  |  ICC-352 Programación Web");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x6E4C30));
        bar.add(lbl);
        return bar;
    }

    // ── Lógica REST ──────────────────────────────────────────────────────────

  
    private void autenticar()
    {
        try {
            String url  = base() + "/api/rest/auth";
            String body = "{\"username\":\"" + txtUsername.getText().trim() +
                    "\",\"password\":\"" + new String(txtPassword.getPassword()) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parsear el JSON correctamente con Jackson
                JsonNode json = mapper.readTree(response.body());
                jwtToken      = json.path("token").asText();
                String rol    = json.path("rol").asText("—");

                lblTokenPreview.setText(jwtToken.length() > 80
                        ? jwtToken.substring(0, 80) + "…" : jwtToken);
                lblEstadoConexion.setText("● Autenticado  [" + txtUsername.getText().trim() + " / " + rol + "]");
                lblEstadoConexion.setForeground(new Color(0x61724A));

                JOptionPane.showMessageDialog(this,
                        "Login exitoso.\nRol: " + rol + "\nToken almacenado correctamente.",
                        "Autenticación JWT", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JsonNode json  = mapper.readTree(response.body());
                String  error  = json.path("error").asText("Error desconocido");
                JOptionPane.showMessageDialog(this,
                        "Error " + response.statusCode() + ": " + error,
                        "Error de autenticación", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo conectar al servidor:\n" + ex.getMessage(),
                    "Error de conexión", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * GET /api/rest/formularios/mis-registros — lista con Bearer JWT.
     */
    private void listarFormularios()
    {
        if (jwtToken.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero autentica para obtener el token JWT.");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base() + "/api/rest/formularios/mis-registros"))
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode arr = mapper.readTree(response.body());
                modeloTabla.setRowCount(0);
                for (JsonNode f : arr) {
                    String id    = f.path("id").asText("—");
                    String idStr = id.length() > 10 ? id.substring(0, 10) + "…" : id;
                    modeloTabla.addRow(new Object[]{
                            idStr,
                            f.path("nombre").asText("—"),
                            f.path("sector").asText("—"),
                            f.path("nivelEscolar").asText("—")
                    });
                }
                lblTotalRegistros.setText("Total: " + arr.size() + " registros");
            } else {
                JOptionPane.showMessageDialog(this,
                        "Error " + response.statusCode() + " al listar formularios.\n" + response.body(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error de conexión:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void crearFormulario()
    {
        if (jwtToken.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero autentica para obtener el token JWT.");
            return;
        }
        try {
            String jsonBody = String.format(
                    "{\"nombre\":\"%s\",\"sector\":\"%s\",\"nivelEscolar\":\"%s\",\"latitud\":0.0,\"longitud\":0.0,\"fotoBase64\":\"\"}",
                    txtNombre.getText().trim(),
                    txtSector.getText().trim(),
                    cmbNivel.getSelectedItem()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base() + "/api/rest/formularios/crear"))
                    .header("Content-Type",  "application/json")
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode json = mapper.readTree(response.body());
            if (response.statusCode() == 201 && json.path("ok").asBoolean()) {
                txtRespuestaCrear.setText("✓ " + json.path("mensaje").asText());
                txtRespuestaCrear.setForeground(new Color(0x4A5A38));
            } else {
                txtRespuestaCrear.setText("✗ Error " + response.statusCode() + "\n" + response.body());
                txtRespuestaCrear.setForeground(new Color(0xC0392B));
            }
        } catch (Exception ex) {
            txtRespuestaCrear.setText("Error de conexión: " + ex.getMessage());
            txtRespuestaCrear.setForeground(new Color(0xC0392B));
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────────
    private String base()
    {
        return "http://" + txtHost.getText().trim() + ":" + txtPuerto.getText().trim();
    }

    private JButton botonPrimario(String texto)
    {
        JButton btn = new JButton(texto);
        btn.setBackground(new Color(0xC96A3A));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel labelBlanco(String texto)
    {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(0xD4B896));
        return lbl;
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> {
            ClienteRESTJava cliente = new ClienteRESTJava();
            cliente.setLocationRelativeTo(null);
            cliente.setVisible(true);
        });
    }
}