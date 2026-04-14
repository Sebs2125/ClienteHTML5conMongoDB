#!/bin/bash
# ============================================================
#  SCRIPT DE DESPLIEGUE — ClienteHTML5conMongoDB
#  Repositorio: https://github.com/Sebs2125/ClienteHTML5conMongoDB.git
#  Dominio App: grupo3-app1.eeebpu.me  (puerto 7000)
# ============================================================

set -e  # Detener si hay cualquier error

echo "========================================"
echo "  INICIANDO DESPLIEGUE — GRUPO 3"
echo "========================================"

# ── 1. ARCHIVO DE SWAP ────────────────────────────────────
echo ""
echo "[1/8] Configurando SWAP (2 GB)..."
sudo dd if=/dev/zero of=/swapfile count=2048 bs=1MiB
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
sudo cp /etc/fstab /etc/fstab.bak
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo "    SWAP configurado correctamente."

# ── 2. DEPENDENCIAS DEL SISTEMA ───────────────────────────
echo ""
echo "[2/8] Instalando dependencias del sistema..."
sudo apt update && sudo apt -y install \
    zip unzip nmap apache2 certbot tree curl git
echo "    Dependencias instaladas."

# ── 3. INSTALACIÓN DE JAVA 21 ─────────────────────────────
echo ""
echo "[3/8] Instalando Java 21 via SDKMAN..."
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.3-tem
java -version
echo "    Java 21 instalado."

# ── 4. CONFIGURACIÓN DE APACHE ────────────────────────────
echo ""
echo "[4/8] Configurando Apache2..."
sudo service apache2 start
sudo a2enmod ssl
sudo a2enmod proxy
sudo a2enmod proxy_http
sudo a2enmod proxy_wstunnel   # ← necesario para WebSocket (/sincronizar)
sudo a2enmod rewrite
sudo systemctl restart apache2
sudo systemctl status apache2 --no-pager
echo "    Apache2 configurado."

# ── 5. CLONAR REPOSITORIO ─────────────────────────────────
echo ""
echo "[5/8] Clonando repositorio..."
mkdir -p ~/apps
cd ~/apps

# Eliminar versión previa si existe
if [ -d "ClienteHTML5conMongoDB" ]; then
    echo "    Directorio existente encontrado — actualizando..."
    cd ClienteHTML5conMongoDB
    git pull origin main || git pull origin master
    cd ..
else
    git clone https://github.com/Sebs2125/ClienteHTML5conMongoDB.git
fi

echo "    Repositorio clonado en ~/apps/ClienteHTML5conMongoDB"

# ── 6. COMPILAR Y PREPARAR LA APLICACIÓN ─────────────────
echo ""
echo "[6/8] Compilando la aplicación con Gradle..."
cd ~/apps/ClienteHTML5conMongoDB

# Asegurar permisos de ejecución
chmod +x ./gradlew

# Compilar (sin tests para agilizar)
./gradlew build -x test

echo "    Compilación exitosa."

# ── 7. CREAR VIRTUAL HOST DE APACHE ──────────────────────
echo ""
echo "[7/8] Creando Virtual Host de Apache para grupo3-app1.eeebpu.me..."

sudo tee /etc/apache2/sites-available/grupo3-app1.conf > /dev/null << 'EOF'
<VirtualHost *:80>
    ServerName grupo3-app1.eeebpu.me

    # Proxy hacia Javalin (puerto 7000)
    ProxyPreserveHost On
    ProxyPass        /sincronizar ws://localhost:7000/sincronizar
    ProxyPassReverse /sincronizar ws://localhost:7000/sincronizar
    ProxyPass        / http://localhost:7000/
    ProxyPassReverse / http://localhost:7000/

    # Logs
    ErrorLog  ${APACHE_LOG_DIR}/app1-error.log
    CustomLog ${APACHE_LOG_DIR}/app1-access.log combined
</VirtualHost>
EOF

# Deshabilitar sitio por defecto y habilitar el nuevo
sudo a2dissite 000-default.conf 2>/dev/null || true
sudo a2ensite grupo3-app1.conf
sudo systemctl reload apache2
echo "    Virtual host creado y activado."

# ── 8. CERTIFICADO SSL ────────────────────────────────────
echo ""
echo "[8/8] Instalando certificado SSL con Certbot..."
sudo apt install python3-certbot-apache -y
sudo certbot --apache -d grupo3-app1.eeebpu.me --non-interactive --agree-tos -m admin@eeebpu.me || {
    echo "    AVISO: Certbot falló. Continúa sin SSL por ahora."
    echo "    Ejecuta manualmente: sudo certbot --apache -d grupo3-app1.eeebpu.me"
}

# ── LANZAR LA APLICACIÓN ─────────────────────────────────
echo ""
echo "=========================================="
echo "  LANZANDO LA APLICACIÓN (puerto 7000)"
echo "=========================================="

cd ~/apps/ClienteHTML5conMongoDB

# Matar proceso anterior si existe
pkill -f "gradle.*run" 2>/dev/null || true
pkill -f "ClienteHTML5conMongoDB" 2>/dev/null || true
sleep 2

# Lanzar en background con nohup
nohup ./gradlew run > ~/apps/app1.log 2>&1 &
APP_PID=$!
echo "    Aplicación lanzada con PID: $APP_PID"

# Esperar a que el servidor esté listo
echo "    Esperando que el servidor inicie..."
for i in $(seq 1 30); do
    if curl -s http://localhost:7000/api/status > /dev/null 2>&1; then
        echo "    Servidor disponible en http://localhost:7000"
        break
    fi
    sleep 2
    echo "    Intento $i/30..."
done

# ── VERIFICACIONES FINALES ────────────────────────────────
echo ""
echo "=========================================="
echo "  VERIFICACIONES FINALES"
echo "=========================================="

echo ""
echo "→ Estado Apache:"
sudo systemctl status apache2 --no-pager -l | head -5

echo ""
echo "→ Procesos Java activos:"
ps aux | grep java | grep -v grep

echo ""
echo "→ Puerto 7000 escuchando:"
ss -tlnp | grep 7000 || echo "   (aún iniciando...)"

echo ""
echo "→ Prueba local HTTP:"
curl -s http://localhost:7000/api/status && echo " OK" || echo " No responde aún"

echo ""
echo "→ DNS app1:"
nslookup grupo3-app1.eeebpu.me || true

echo ""
echo "→ Certificados SSL:"
sudo certbot certificates 2>/dev/null || echo "   (sin certificados aún)"

echo ""
echo "→ Logs (últimas 10 líneas):"
tail -10 ~/apps/app1.log 2>/dev/null || echo "   (sin logs aún)"

echo ""
echo "========================================"
echo "  DESPLIEGUE COMPLETADO"
echo "========================================"
echo ""
echo "  App URL:    https://grupo3-app1.eeebpu.me"
echo "  Local:      http://localhost:7000"
echo "  gRPC:       puerto 50051"
echo "  Logs:       tail -f ~/apps/app1.log"
echo ""
echo "  Para relanzar la app manualmente:"
echo "  cd ~/apps/ClienteHTML5conMongoDB && nohup ./gradlew run > ~/apps/app1.log 2>&1 &"
echo ""
echo "  Para ver el Virtual Host de Apache:"
echo "  cat /etc/apache2/sites-available/grupo3-app1.conf"
echo "========================================"
