package Modelos;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.example.grpc.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;


public class ClienteGRPCJava extends JFrame
{
    private ManagedChannel                            channel;
    private EncuestaServicioGrpc.EncuestaServicioBlockingStub stub;

    // Conexión
    private JTextField txtHost;
    private JTextField txtPuerto;
    private JLabel     lblEstado;

    // Crear formulario
    private JTextField    txtUsuarioCrear;
    private JTextField    txtNombre;
    private JTextField    txtSector;
    private JComboBox<String> cmbNivel;
    private JTextField    txtLatitud;
    private JTextField    txtLongitud;
    private JTextArea     txtRespuestaCrear;


    private JTextField        txtUsuarioListar;
    private JTable            tablaFormularios;
    private DefaultTableModel modeloTabla;
    private JLabel            lblTotal;

    public ClienteGRPCJava()
    {
        setTitle("Cliente gRPC — Encuestas PUCMM");
        setSize(920, 660);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        aplicarEstilo();

        add(crearPanelConexion(), BorderLayout.NORTH);
        add(crearTabs(),          BorderLayout.CENTER);
        add(crearStatusBar(),     BorderLayout.SOUTH);
    }

    private void aplicarEstilo()
    {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        getContentPane().setBackground(new Color(0xFAF6F0));
    }

    private JPanel crearPanelConexion()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBackground(new Color(0x2D2A26));
        panel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel titulo = new JLabel("  Encuestas PUCMM — Cliente gRPC");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 14));
        panel.add(titulo);

        panel.add(Box.createHorizontalStrut(20));
        panel.add(labelBlanco("Host:"));
        txtHost = new JTextField("localhost", 10);
        panel.add(txtHost);

        panel.add(labelBlanco("Puerto:"));
        txtPuerto = new JTextField("50051", 6);
        panel.add(txtPuerto);

        JButton btnConectar = botonPrimario("Conectar");
        btnConectar.addActionListener(e -> conectar());
        panel.add(btnConectar);

        JButton btnDesconectar = new JButton("Desconectar");
        btnDesconectar.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnDesconectar.addActionListener(e -> desconectar());
        panel.add(btnDesconectar);

        lblEstado = new JLabel("● Desconectado");
        lblEstado.setForeground(new Color(0xBF9870));
        lblEstado.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(lblEstado);

        return panel;
    }

    private JTabbedPane crearTabs()
    {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tabs.addTab("📋  Listar Formularios",  crearTabListar());
        tabs.addTab("➕  Crear Formulario",    crearTabCrear());
        return tabs;
    }

    private JPanel crearTabListar()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(0xFAF6F0));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Barra de filtro
        JPanel barraTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        barraTop.setBackground(new Color(0xFAF6F0));
        barraTop.add(new JLabel("Usuario:"));
        txtUsuarioListar = new JTextField("admin", 14);
        barraTop.add(txtUsuarioListar);

        JButton btnListar = botonPrimario("Listar vía gRPC");
        btnListar.addActionListener(e -> listarFormularios());
        barraTop.add(btnListar);

        lblTotal = new JLabel("Total: 0 registros");
        lblTotal.setForeground(new Color(0x6B6258));
        barraTop.add(lblTotal);

        // Tabla
        String[] cols = { "ID (parcial)", "Nombre", "Sector", "Nivel Escolar", "Latitud", "Longitud" };
        modeloTabla = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaFormularios = new JTable(modeloTabla);
        tablaFormularios.setRowHeight(26);
        tablaFormularios.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tablaFormularios.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        tablaFormularios.setSelectionBackground(new Color(0xF5EBE0));
        tablaFormularios.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        tablaFormularios.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting() && tablaFormularios.getSelectedRow() >= 0) {
                mostrarDetalleSeleccionado();
            }
        });

        JScrollPane scroll = new JScrollPane(tablaFormularios);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xE8D5C0)));

        panel.add(barraTop, BorderLayout.NORTH);
        panel.add(scroll,   BorderLayout.CENTER);
        panel.add(crearPanelDetalle(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel panelDetalle;
    private JLabel lblDetalleNombre, lblDetalleSector, lblDetalleNivel, lblDetalleGeo;

    private JPanel crearPanelDetalle()
    {
        panelDetalle = new JPanel(new GridLayout(2, 4, 8, 4));
        panelDetalle.setBackground(new Color(0xF5EBE0));
        panelDetalle.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE8D5C0)),
                new EmptyBorder(8, 12, 8, 12)));

        panelDetalle.add(etiquetaGris("Nombre:"));
        lblDetalleNombre = new JLabel("—");
        panelDetalle.add(lblDetalleNombre);

        panelDetalle.add(etiquetaGris("Sector:"));
        lblDetalleSector = new JLabel("—");
        panelDetalle.add(lblDetalleSector);

        panelDetalle.add(etiquetaGris("Nivel:"));
        lblDetalleNivel = new JLabel("—");
        panelDetalle.add(lblDetalleNivel);

        panelDetalle.add(etiquetaGris("Coordenadas:"));
        lblDetalleGeo = new JLabel("—");
        panelDetalle.add(lblDetalleGeo);

        return panelDetalle;
    }

    private void mostrarDetalleSeleccionado()
    {
        int row = tablaFormularios.getSelectedRow();
        if (row < 0) return;
        lblDetalleNombre.setText((String) modeloTabla.getValueAt(row, 1));
        lblDetalleSector.setText((String) modeloTabla.getValueAt(row, 2));
        lblDetalleNivel.setText((String)  modeloTabla.getValueAt(row, 3));
        lblDetalleGeo.setText(modeloTabla.getValueAt(row, 4) + " / " + modeloTabla.getValueAt(row, 5));
    }

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
        JLabel titulo = new JLabel("Crear Formulario vía gRPC");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        titulo.setForeground(new Color(0x2D2A26));
        panel.add(titulo, g);

        g.gridy++; g.gridwidth = 1;
        panel.add(new JLabel("Usuario registra:"), g);
        g.gridx = 1;
        txtUsuarioCrear = new JTextField("admin", 20);
        panel.add(txtUsuarioCrear, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Nombre encuestado:"), g);
        g.gridx = 1;
        txtNombre = new JTextField(20);
        panel.add(txtNombre, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Sector:"), g);
        g.gridx = 1;
        txtSector = new JTextField(20);
        panel.add(txtSector, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Nivel Escolar:"), g);
        g.gridx = 1;
        cmbNivel = new JComboBox<>(new String[]{ "BASICO", "MEDIO", "UNIVERSITARIO", "POSTGRADO", "DOCTORADO" });
        panel.add(cmbNivel, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Latitud:"), g);
        g.gridx = 1;
        txtLatitud = new JTextField("18.4861", 20);
        panel.add(txtLatitud, g);

        g.gridx = 0; g.gridy++;
        panel.add(new JLabel("Longitud:"), g);
        g.gridx = 1;
        txtLongitud = new JTextField("-69.9312", 20);
        panel.add(txtLongitud, g);

        g.gridx = 0; g.gridy++; g.gridwidth = 2;
        JButton btnCrear = botonPrimario("Enviar vía gRPC — CrearFormulario");
        btnCrear.addActionListener(e -> crearFormulario());
        panel.add(btnCrear, g);

        g.gridy++;
        JLabel lblRes = new JLabel("Respuesta del servidor gRPC:");
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
        JLabel lbl = new JLabel("Encuestas PUCMM — Cliente gRPC  |  Puerto 50051  |  ICC-352 Programación Web");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x6E4C30));
        bar.add(lbl);
        return bar;
    }


    private void conectar()
    {
        try {
            if (channel != null && !channel.isShutdown()) channel.shutdown();

            channel = ManagedChannelBuilder
                    .forAddress(txtHost.getText().trim(), Integer.parseInt(txtPuerto.getText().trim()))
                    .usePlaintext()
                    .build();

            stub = EncuestaServicioGrpc.newBlockingStub(channel);

            lblEstado.setText("● Conectado  →  " + txtHost.getText().trim() + ":" + txtPuerto.getText().trim());
            lblEstado.setForeground(new Color(0x61724A));

            JOptionPane.showMessageDialog(this,
                    "Canal gRPC creado hacia " + txtHost.getText().trim() + ":" + txtPuerto.getText().trim(),
                    "Conexión establecida", JOptionPane.INFORMATION_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "El puerto debe ser un número.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            lblEstado.setText("● Error de conexión");
            lblEstado.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this,
                    "No se pudo crear el canal gRPC:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void desconectar()
    {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            stub = null;
            lblEstado.setText("● Desconectado");
            lblEstado.setForeground(new Color(0xBF9870));
        }
    }


    private void crearFormulario()
    {
        if (!verificarConexion()) return;

        try {
            double lat = Double.parseDouble(txtLatitud.getText().trim());
            double lng = Double.parseDouble(txtLongitud.getText().trim());

            FormularioRequest request = FormularioRequest.newBuilder()
                    .setNombre(txtNombre.getText().trim())
                    .setSector(txtSector.getText().trim())
                    .setNivelEscolar((String) cmbNivel.getSelectedItem())
                    .setUsuarioRegistro(txtUsuarioCrear.getText().trim())
                    .setLatitud(lat)
                    .setLongitud(lng)
                    .setFotoBase64("")
                    .build();

            FormularioRespuesta respuesta = stub.crearFormulario(request);

            if (respuesta.getExito()) {
                txtRespuestaCrear.setText("✓ Éxito\n" + respuesta.getMensaje());
                txtRespuestaCrear.setForeground(new Color(0x4A5A38));
            } else {
                txtRespuestaCrear.setText("✗ Error del servidor gRPC:\n" + respuesta.getMensaje());
                txtRespuestaCrear.setForeground(new Color(0xC0392B));
            }

        } catch (NumberFormatException ex) {
            txtRespuestaCrear.setText("✗ Latitud y longitud deben ser números válidos.");
            txtRespuestaCrear.setForeground(new Color(0xC0392B));
        } catch (StatusRuntimeException ex) {
            txtRespuestaCrear.setText("✗ Error gRPC: " + ex.getStatus().getDescription());
            txtRespuestaCrear.setForeground(new Color(0xC0392B));
        } catch (Exception ex) {
            txtRespuestaCrear.setText("✗ Error inesperado: " + ex.getMessage());
            txtRespuestaCrear.setForeground(new Color(0xC0392B));
        }
    }


    private void listarFormularios()
    {
        if (!verificarConexion()) return;

        String usuario = txtUsuarioListar.getText().trim();
        if (usuario.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Introduce un nombre de usuario para listar.", "Campo requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            UsuarioRequest request = UsuarioRequest.newBuilder()
                    .setUsuario(usuario)
                    .build();

            ListaFormulariosRespuesta respuesta = stub.listarFormulariosPorUsuario(request);
            List<FormularioData>      lista     = respuesta.getFormulariosList();

            modeloTabla.setRowCount(0);
            for (FormularioData f : lista) {
                String idCorto = f.getId().length() > 10
                        ? f.getId().substring(0, 10) + "…" : f.getId();
                modeloTabla.addRow(new Object[]{
                        idCorto,
                        f.getNombre(),
                        f.getSector(),
                        f.getNivelEscolar(),
                        String.format("%.4f", f.getLatitud()),
                        String.format("%.4f", f.getLongitud())
                });
            }

            lblTotal.setText("Total: " + lista.size() + " registros");

            if (lista.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No se encontraron formularios para el usuario: " + usuario,
                        "Sin resultados", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (StatusRuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error gRPC al listar:\n" + ex.getStatus().getDescription(),
                    "Error gRPC", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error inesperado:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private boolean verificarConexion()
    {
        if (stub == null || (channel != null && channel.isShutdown())) {
            JOptionPane.showMessageDialog(this,
                    "Primero conecta al servidor gRPC.",
                    "Sin conexión", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
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

    private JLabel etiquetaGris(String texto)
    {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(new Color(0x6B6258));
        return lbl;
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> {
            ClienteGRPCJava cliente = new ClienteGRPCJava();
            cliente.setLocationRelativeTo(null);
            cliente.setVisible(true);
        });
    }
}