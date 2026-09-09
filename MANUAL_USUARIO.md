# 📖 Manual de Usuario - Sistema Boxy ERP
### Sistema Integral de Inventarios, Punto de Venta (POS), Compras y Administración Multi-sucursal

---

## 📌 Introducción

**Boxy** es una plataforma integral diseñada para simplificar y automatizar el control operativo y comercial de tu negocio:
* **Inventario y Almacenes:** Control de existencias en tiempo real, múltiples sucursales, transferencias entre bodegas y kardex detallado.
* **Punto de Venta (POS) y Clientes:** Cobro ágil en caja, escaneo de códigos de barra, cotizaciones y control de crédito a clientes.
* **Compras y Proveedores:** Registro de pedidos a proveedores y recepción física de mercancía con costeo automático.
* **Reportes y Finanzas:** Indicadores clave de rendimiento (KPIs), utilidades brutas, valor del inventario y rotación de productos.
* **Administración y Seguridad:** Control estricto de roles y permisos para tus empleados.

---

## 👥 1. Roles de Usuarios y Accesos

El sistema cuenta con **4 perfiles de acceso**. Cada colaborador solo puede ver y operar las secciones autorizadas para su función:

| Rol | Destinado a | ¿Qué puede hacer en el sistema? |
|---|---|---|
| **Super Administrador** | Dueño / Dirección General | **Acceso total e irrestricto.** Puede ver y editar todo: finanzas, crear sucursales, dar de alta usuarios y cambiar cualquier configuración general. |
| **Administrador** | Administrador de Operaciones | Gestiona compras, ventas, inventario, catálogos, proveedores, clientes y consulta todos los reportes operativos y analíticos. |
| **Vendedor** | Cajeros y Personal de Ventas | Opera el **Punto de Venta (POS)**, abre y cierra su turno de caja, busca productos, registra clientes nuevos y crea cotizaciones. *(No ve costos de compra ni márgenes de utilidad)*. |
| **Almacén** | Encargados de Bodega / Logística | Controla existencias, registra entradas físicas de mercancía recibida de proveedores, realiza transferencias entre almacenes y ajustes por merma o auditoría. |

---

## 🏬 2. Módulo de Inventario

### 2.1. Catálogo de Productos (`Inventario > Productos`)
Aquí se consulta y administra todo el catálogo de artículos disponibles en la empresa.

* **Búsqueda Inteligente:** Encuentra cualquier artículo por su **Nombre**, código **SKU** o **Código de Barras**.
* **Filtros Dinámicos:** Filtra por Categoría, Marca o estado del stock (*Agotado*, *Stock Bajo*, *Disponible*).
* **Carga Masiva desde Excel (Recomendado):**
  1. Haz clic en el botón verde **"Importar Excel"** en la parte superior derecha.
  2. Arrastra o selecciona tu archivo `.xlsx` o `.csv` (por ejemplo, el inventario general con columnas: *Código, Producto, Categoría, Costo, Precio, Existencia*).
  3. El sistema procesará el archivo en pocos segundos, creando automáticamente las categorías, marcas y cargando las existencias iniciales al almacén principal.
* **Creación Manual:** Pulsa **"Nuevo Producto"** para registrar un artículo con su código de barras, precio de venta, costo de compra, categoría y unidad de medida.

---

### 2.2. Almacenes (`Inventario > Almacenes`)
Boxy permite organizar la mercancía en diferentes ubicaciones físicas o virtuales:
* **Almacén Central:** Es el almacén principal donde entra la mercancía por defecto.
* **Almacenes por Sucursal:** Almacenes asignados a sucursales secundarias o áreas de reserva.
* **Información disponible por almacén:**
  - Número de productos distintos en existencia.
  - Valor total del inventario (cuánto dinero representa el stock valuado a costo de adquisición).
  - Estado operativo (*Activo* / *Inactivo*).

---

### 2.3. Kardex y Movimientos (`Inventario > Movimientos`)
El **Kardex** es la bitácora contable de tu almacén. Ningún producto entra o sale sin quedar debidamente registrado:
* **Entradas por Compra (`PURCHASE_IN`):** Mercancía que llegó de un proveedor y fue recibida físicamente.
* **Salidas por Venta (`SALE_OUT`):** Productos cobrados en caja o facturados en el POS.
* **Transferencias (`TRANSFER`):** Traspasos de artículos entre bodegas.
* **Ajustes (`ADJUSTMENT`):** Modificaciones por mermas o recuentos físicos.

---

### 2.4. Transferencias entre Almacenes (`Inventario > Transferencias`)
Cuando necesites enviar mercancía de una sucursal a otra:
1. Haz clic en **"Nueva Transferencia"**.
2. Selecciona el **Almacén Origen** (de dónde sale) y el **Almacén Destino** (a dónde llega).
3. Agrega los productos y las cantidades a mover.
4. **Flujo de control:**
   - **Solicitada / Pendiente:** Se genera la solicitud de traspaso.
   - **Despachada:** El producto sale del stock disponible y se marca como *"En Tránsito"*.
   - **Recibida:** El encargado del almacén destino confirma que los paquetes llegaron físicamente; en ese momento el stock se suma automáticamente al nuevo almacén.

---

### 2.5. Ajustes de Inventario (`Inventario > Ajustes`)
Úsalo cuando realices conteos físicos periódicos o si un producto se dañó:
1. Haz clic en **"Nuevo Ajuste"**.
2. Selecciona el almacén y el producto.
3. Elige el motivo oficial:
   - *Conteo Físico / Auditoría* (para cuadrar el stock real con el sistema).
   - *Merma / Daño de Mercancía* (producto roto o deteriorado).
   - *Caducidad de Producto* (artículos vencidos que se deben retirar).
4. Escribe la diferencia (positiva si sobró, negativa si faltó) y guarda. El sistema ajustará el stock inmediatamente y dejará constancia en la bitácora.

---

## 🛒 3. Módulo de Ventas y Punto de Venta (POS)

### 3.1. Operación del Punto de Venta (`Ventas > Punto de Venta`)
Diseñado para que la atención y el cobro en mostrador sean inmediatos:

#### Paso 1: Apertura de Caja (Turno de Trabajo)
* Al comenzar la jornada, el cajero ingresa el dinero en efectivo con el que arranca su gaveta (*Monto inicial de apertura*).
* Si es la primera vez que entra, el sistema le habilita automáticamente su turno.

#### Paso 2: Agregar Productos al Ticket
* **Con Lector de Código de Barras:** Pasa el escáner sobre el producto; se añadirá al ticket al instante.
* **Búsqueda Manual:** Teclea el nombre o SKU en la barra superior.
* **Catálogo Táctil:** Haz clic en los botones de productos en pantalla para sumarlos al carrito.
* **Modificar Cantidad:** Puedes hacer clic en `+` o `-`, o utilizar el teclado numérico en pantalla.

#### Paso 3: Selección del Cliente
* **Cliente de Mostrador (Por Defecto):** La venta se asigna automáticamente a **"Público General"** sin pedir datos adicionales.
* **Cliente Frecuente / Factura:** Haz clic en *"Buscar Cliente"* para seleccionar un cliente registrado o crear uno nuevo en segundos.

#### Paso 4: Cobro y Emisión de Ticket
1. Presiona el botón verde **"Cobrar"** o pulsa la barra espaciadora.
2. Selecciona el método de pago:
   - 💵 **Efectivo:** Escribe cuánto entrega el cliente; el sistema calcula el cambio exacto.
   - 💳 **Tarjeta (Débito/Crédito):** Registra el cobro por terminal bancaria.
   - 🏦 **Transferencia:** Ingresa el número de comprobante o referencia.
3. Haz clic en **"Confirmar Cobro"**. El sistema:
   - Emite el ticket/factura foliado.
   - **Descuenta de inmediato los productos del inventario.**
   - Suma el importe a la caja del cajero.

#### Paso 5: Cierre de Turno de Caja (Arqueo)
Al terminar la jornada:
1. Ve a la opción **"Cerrar Turno"**.
2. El cajero cuenta el dinero físico de la gaveta e introduce el total.
3. El sistema compara el efectivo esperado contra el real, indicando si el arqueo fue exacto o si hubo sobrante/faltante, quedando registrado para auditoría.

---

### 3.2. Clientes (`Ventas > Clientes`)
* Directorio de clientes con su RFC / Identificación Oficial, teléfono, correo electrónico y dirección.
* **Límite de Crédito:** Puedes definir un monto máximo de crédito para clientes de confianza y consultar en tiempo real cuánto saldo tienen pendiente por liquidar.

---

### 3.3. Cotizaciones (`Ventas > Cotizaciones`)
* Crea presupuestos formales para tus clientes con vigencia.
* **Conversión a Venta en 1 Clic:** Cuando el cliente apruebe la cotización, haz clic en **"Convertir a Venta"** y todos los artículos se transferirán directamente al Punto de Venta para ser cobrados, sin necesidad de capturarlos otra vez.

---

## 📦 4. Módulo de Compras y Proveedores

### 4.1. Proveedores (`Compras > Proveedores`)
Directorio de empresas mayoristas a las que les compras mercancía, con sus días de crédito pactados y datos de contacto del agente de ventas.

### 4.2. Órdenes de Compra (`Compras > Órdenes de Compra`)
1. Crea un borrador con los productos que necesitas surtir y los precios de costo acordados con el proveedor.
2. **Aprobación:** El Gerente aprueba la orden.
3. **Enviar a Proveedor:** Se marca como pedida formalmente.

### 4.3. Recepción de Mercancía (`Compras > Recepción`)
Cuando el camión del proveedor llega a tu sucursal con las cajas:
1. En la pestaña **"Pendientes de Recepción"**, busca la orden de compra correspondiente.
2. Verifica físicamente las cantidades recibidas.
3. Al hacer clic en **"Recibir Mercancía"**, el sistema:
   - Sube automáticamente las existencias de todos esos productos en el almacén.
   - Actualiza el costo de adquisición de los artículos si hubo cambio de precio.
   - Deja constancia de la entrada en el Kardex.

---

## 📊 5. Módulo de Reportes y Analítica

Permite monitorear la salud financiera y comercial de tu empresa con filtros por sucursal y rango de fechas:

* **Reportes de Ventas:**
  - Ingresos totales, ticket promedio diario y cantidad de transacciones.
  - Gráfico de tendencia de facturación (cuáles días de la semana se vende más).
  - Listado de los productos más vendidos y rendimiento por empleado.
* **Reportes de Inventario:**
  - **Valorización Económica del Stock:** Cuánto capital tienes invertido en mercancía.
  - **Alertas de Stock Crítico:** Lista de productos que están a punto de agotarse o ya se quedaron en 0 para reordenar a tiempo.
  - **Rotación de Inventario:** Qué artículos se venden con alta frecuencia y cuáles son artículos lentos (*huesos*).
* **Reportes Financieros:**
  - Estado de resultados simplificado: Ingresos brutos vs. Costo de ventas (COGS) vs. Ganancia bruta.
  - Total de impuestos recaudados (IVA).
  - Desglose de ingresos por forma de pago (Efectivo vs. Tarjetas vs. Transferencias).

---

## ⚙️ 6. Módulo de Administración y Configuración

### 6.1. Usuarios (`Administración > Usuarios`)
* Alta de nuevos empleados que utilizarán el sistema.
* Solo requieres escribir su nombre, correo electrónico, elegir su **Rol** (Super Administrador, Administrador, Vendedor o Almacén) y seleccionar en qué sucursal trabajará.
* El sistema garantiza que no se puedan duplicar correos electrónicos ni nombres de usuario.

### 6.2. Sucursales (`Administración > Configuración > Sucursales`)
* Creación de nuevas sedes físicas, tiendas o bodegas con su dirección y teléfono.

### 6.3. Impuestos (`Administración > Configuración > Impuestos`)
* Configuración de tasas fiscales aplicables a las ventas (por ejemplo, IVA 18%, IVA 16% o Tasa 0% Exento).

### 6.4. Bitácora de Auditoría (`Administración > Configuración > Auditoría`)
* Registro cronológico para el dueño del negocio sobre quién inició sesión, quién modificó un producto, quién canceló una venta o quién aplicó un ajuste de inventario.

---

## 💡 7. Preguntas Frecuentes (FAQ)

#### ¿Puedo usar el sistema desde una tablet o teléfono?
**Sí.** El diseño de Boxy es totalmente responsivo (*mobile-friendly*), adaptando la pantalla del Punto de Venta y los reportes a pantallas táctiles de tablets y celulares.

#### ¿Qué sucede si el internet se desconecta un momento mientras cobro?
El Punto de Venta valida localmente y cuenta con llaves de idempotencia; en cuanto vuelve la conexión, reenvía la confirmación evitando que una venta se cobre por duplicado.

#### ¿Cómo doy de alta un producto nuevo rápidamente?
Ve a `Inventario > Productos`, pulsa en el botón azul **"Nuevo Producto"**, escribe el nombre, código de barras, precio de venta, costo y selecciona su categoría. Quedará disponible inmediatamente en el Punto de Venta.

#### ¿El sistema descuenta stock inmediatamente tras vender en POS?
**Sí.** Cada vez que se emite un ticket en el Punto de Venta, el sistema ejecuta una transacción atómica que descuenta las unidades de la base de datos y deja constancia en el Kardex en tiempo real.
