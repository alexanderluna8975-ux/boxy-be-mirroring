# GEMINI.md

## 1. Project Overview

Enterprise Multi-Branch Inventory & Sales Management Platform (Backend & Database).

This is an enterprise-grade, multi-branch inventory, purchasing, point of sale (POS), and sales management backend platform built with **Java 21**, **Spring Boot 3**, and **MySQL 8 (InnoDB)**, designed for strict ACID transactional integrity, high concurrency, multi-location data isolation, complete auditability, and seamless interoperability with the **Angular 21** frontend (`boxy-fe`).

---

## 2. Technology & Architectural Foundations

### 2.1. Backend Architecture: Clean Architecture / Hexagonal / DDD
The backend system strictly follows **Clean Architecture** and **Domain-Driven Design (DDD)** principles, structured into four decoupled layers:

1. **Domain Layer (Core - Zero external dependencies):**
   - Pure Domain Entities & Value Objects (e.g., `Money`, `Sku`, `TaxRate`, `Quantity`).
   - Domain Events (e.g., `StockDepletedEvent`, `GoodsReceivedEvent`, `CashierSessionClosedEvent`).
   - Domain Repository Interfaces (Ports).
   - Domain-level business rules and validation invariants.
   - **Rule:** Never import JPA/Hibernate annotations, Spring framework beans, or HTTP primitives into the Domain Layer.

2. **Application Layer (Use Cases / CQRS):**
   - **Commands (Write operations):** Use cases modifying system state executed inside `@Transactional(isolation = Isolation.READ_COMMITTED)`.
   - **Queries (Read operations):** Optimized, read-only use cases with projection DTOs for fast UI rendering.
   - Request/Response DTOs specifically aligned with Angular 21 interfaces.
   - External service interfaces (PDF generation, fiscal printer integration, email/SMS dispatchers).

3. **Infrastructure Layer (Secondary Adapters):**
   - Database persistence (Spring Data JPA, Hibernate with `MySQLDialect`, Flyway migrations).
   - Redis caching for sessions, token blacklists, and distributed idempotency locks.
   - Third-party implementations.

4. **Presentation / API Layer (Primary Adapters):**
   - RESTful API Controllers with explicit HTTP verb mapping.
   - Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@DecimalMin`).
   - Spring Security 6 with stateless JWT authentication filter and branch context extraction.
   - Role-Based Access Control (RBAC) via `@PreAuthorize`.
   - Global `@RestControllerAdvice` formatting all errors to RFC 7807 problem details.

---

## 3. Angular 21 Frontend Interoperability Rules

The frontend is built on **Angular 21** utilizing standalone components, reactive forms, Signals (`signal()`, `computed()`), and OnPush/Zoneless change detection. The backend must strictly honor the following contract rules:

1. **Exact Property Naming:**
   - DTO JSON serialization MUST use `camelCase` (e.g., `sellingPrice`, `warehouseId`, `minStockAlert`, `createdAt`).
   - Database column names MUST use `snake_case` (e.g., `selling_price`, `warehouse_id`), mapped via JPA `@Column(name = "...")` or Jackson naming strategy.
2. **Date & Time Standards:**
   - All timestamps must be returned as ISO-8601 UTC strings with `Z` suffix (e.g., `2026-08-19T22:45:00.000Z`).
   - Format: `Instant` / `OffsetDateTime` serialized via `Jackson2ObjectMapperBuilder`.
3. **Numeric Precision for Angular Signals:**
   - Prices, subtotals, taxes, and totals MUST be serialized as numeric primitives (`BigDecimal` serialized to JSON `number`) rounded to 2 or 4 decimals (e.g., `129.50`), avoiding string wrapping unless explicit currency formatting is requested.
4. **Signal-Friendly Paginated Responses:**
   - Always return unified pagination meta structures that Angular 21 table signals can consume without transformation.
5. **No Undefined Nulls:**
   - Missing or non-applicable values must be explicitly `null`, never omitted keys, preventing signal reactivity glitches in Angular templates.

---

## 4. Database Architecture & Relational Design (MySQL 8 - InnoDB)

### 4.1. Storage Engine, Charset & Collation
- **Storage Engine:** Strictly `ENGINE=InnoDB` for all tables (ACID transactions, row-level locking, foreign keys).
- **Character Set & Collation:** `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`.

### 4.2. Data Types & Precision Standards
- **Monetary Values & Unit Costs:** `DECIMAL(14, 4)`. **NEVER USE `FLOAT` OR `DOUBLE`**.
- **Stock Quantities:** `DECIMAL(12, 4)` to natively support fractional inventory (e.g., Kg, Liters, Meters) and discrete units (Pieces).
- **Timestamps:** `DATETIME(6)` or `TIMESTAMP(6)` stored in UTC.
- **Primary Keys:** `VARCHAR(36)` (UUID v4) for domain resources exposed over API; `BIGINT UNSIGNED AUTO_INCREMENT` for high-throughput immutable logs (`stock_movements`, `audit_logs`).
- **Flexible Attributes:** `JSON` data type for product variant specifications and dynamic branch configs.

### 4.3. Mandatory Audit Columns
Every persistent business entity MUST include:
```sql
created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
created_by VARCHAR(36) NULL,
updated_by VARCHAR(36) NULL,
deleted_at DATETIME(6) NULL, -- Soft deletes
CONSTRAINT fk_{table}_created_by FOREIGN KEY (created_by) REFERENCES users(id),
CONSTRAINT fk_{table}_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
```

### 4.4. Immutable Append-Only Tables
Updates (`UPDATE`) and deletions (`DELETE`) are strictly forbidden on the following tables (only `INSERT` operations allowed):
- `stock_movements` (Kardex audit log)
- `cashier_session_movements`
- `audit_logs`
- `invoices` & `invoice_items` (once issued, corrections require a `credit_note`)
- `payment_transactions`

---

## 5. Domain Schema & Entity Models

### 5.1. Authentication & Multi-Branch Tenancy
- **`companies`**: Organization profile, base currency, tax identification, negative stock allowance (`allow_negative_stock: boolean`).
- **`branches`**: Physical locations/stores with separate address, contact, and default warehouse.
- **`warehouses`**: Storage facilities belonging to specific branches (`branch_id`).
- **`users`**: Authentication credentials (hashed using Argon2id/BCrypt), email (unique), full name, state (`ACTIVE`, `INACTIVE`, `SUSPENDED`).
- **`roles` & `permissions`**: Granular RBAC permissions (`inventory:read`, `inventory:create`, `sales:checkout`, `transfers:approve`, etc.).
- **`user_branch_assignments`**: M:N mapping of users to allowed branches with contextual role override per branch.

### 5.2. Catalog & Inventory Management
- **`categories`** & **`brands`**: Hierarchical category tree (`parent_id`) and brand catalog.
- **`units_of_measure`**: Standardized units (Code: `NIU`, `KGM`, `LTR`, `BOX`, etc.) with decimal conversion factors.
- **`products`**:
  - `id`, `company_id`, `category_id`, `brand_id`, `unit_id`, `sku` (UNIQUE), `barcode` (UNIQUE, INDEXED), `name`, `description`, `cost_price`, `selling_price`, `min_stock_alert`, `has_variants`, `is_active`.
- **`product_variants`**:
  - `id`, `product_id`, `variant_sku` (UNIQUE), `barcode`, `attributes` (JSON), `cost_adjustment`, `price_adjustment`.
- **`stock_levels` (Current Stock Per Location)**:
  - Composite Unique constraint on `(product_id, warehouse_id)`.
  - `quantity_available`: Physical on-hand stock ready for sale/transfer.
  - `quantity_reserved`: Stock committed to pending sales orders or transfer requests.
  - `quantity_in_transit`: Stock dispatched from origin warehouse awaiting receipt at destination.
- **`stock_movements` (Double-Entry Kardex Engine)**:
  - `id`, `warehouse_id`, `product_id`, `movement_type` (`PURCHASE_IN`, `SALE_OUT`, `TRANSFER_OUT`, `TRANSFER_IN`, `ADJUSTMENT_POS`, `ADJUSTMENT_NEG`, `RETURN_IN`), `quantity`, `unit_cost`, `balance_after`, `reference_type`, `reference_id`, `notes`, `created_at`.
- **`stock_transfers` & `stock_transfer_items`**:
  - Multi-stage transfer state machine: `DRAFT` → `REQUESTED` → `IN_TRANSIT` → `RECEIVED` | `REJECTED` | `CANCELLED`.

### 5.3. Purchasing & Supplier Management
- **`suppliers`**: Tax ID, legal name, commercial name, payment terms (net days), email, phone, contact person.
- **`purchase_orders` & `purchase_order_items`**:
  - Lifecycle: `DRAFT` → `ISSUED` → `APPROVED` → `PARTIALLY_RECEIVED` → `COMPLETED` → `CANCELLED`.
- **`goods_receipts` & `goods_receipt_items`**:
  - Physical warehouse intake linked to purchase orders; automatically triggers `stock_movements` entries and recalculates moving weighted average cost (WAC).

### 5.4. Sales & POS (Point of Sale)
- **`customers`**: Tax ID / Document number, full name, email, phone, address, credit limit, current credit balance.
- **`cashier_sessions` (Cash Drawers / Shifts)**:
  - `id`, `branch_id`, `user_id`, `opened_at`, `closed_at`, `initial_cash`, `expected_cash`, `actual_cash`, `difference`, `status` (`OPEN`, `CLOSED`).
- **`sales_orders` & `sales_order_items`**:
  - Quotations and active sales orders (`QUOTATION`, `CONFIRMED`, `PAID`, `CANCELLED`).
- **`invoices` / `receipts`**:
  - `id`, `branch_id`, `cashier_session_id`, `customer_id`, `document_type` (`INVOICE`, `TICKET`, `RECEIPT`), `series`, `number`, `subtotal`, `discount_amount`, `tax_amount`, `total_amount`, `status` (`ISSUED`, `VOIDED`).
- **`payments`**:
  - `id`, `invoice_id`, `payment_method` (`CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `STORE_CREDIT`), `amount`, `reference_code`, `status` (`CONFIRMED`, `FAILED`).
- **`credit_notes` / `sales_returns`**:
  - Return documents referencing original invoices with automatic inventory restitution.

---

## 6. Critical Business Invariants & Concurrency Rules

### 6.1. Race Condition Prevention & Negative Stock Invariant
- Every stock reduction operation (POS checkout, transfer dispatch) MUST acquire a pessimistic row lock inside an isolated database transaction:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM StockLevel s WHERE s.product.id = :productId AND s.warehouse.id = :warehouseId")
  Optional<StockLevel> findForUpdate(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);
  ```
  *(Translates to `SELECT ... FOR UPDATE` in MySQL InnoDB)*.
- If `quantity_available < requested_quantity` and `company.allow_negative_stock == false`:
  - Throw `InsufficientStockException`.
  - Global filter rolls back transaction and returns HTTP 409 Conflict with code `INSUFFICIENT_STOCK`.

### 6.2. Idempotency Key Specification
- All financial and checkout endpoints (`POST /api/v1/sales/checkout`, `POST /api/v1/payments`) MUST enforce the `X-Idempotency-Key` header (UUID format).
- Validated via Redis or database lock table:
  - If a concurrent request is processing: Return HTTP 409 `OPERATION_IN_PROGRESS`.
  - If the operation already succeeded: Return cached response without re-executing business logic.

### 6.3. Shift / POS Invariant
- A cashier CANNOT process a checkout or receipt without an active `cashier_session` in status `OPEN` tied to the user's active branch.

### 6.4. Multi-Tenant Branch Isolation
- All query handlers and JPA repositories MUST enforce branch isolation based on the authenticated user's JWT claims:
  - If user is `BRANCH_MANAGER`, `CASHIER`, or `WAREHOUSE_KEEPER`, filter automatically by `branch_id`.
  - Only users with `GLOBAL_ADMIN` scope can execute cross-branch consolidated queries.

---

## 7. API REST Contract Standards

### 7.1. Base URL & Versioning
- Standard: `/api/v1/{resource}`
- Resources in plural kebab-case: `/api/v1/purchase-orders`, `/api/v1/stock-transfers`, `/api/v1/cashier-sessions`.

### 7.2. Standard Success Response Envelope
```json
{
  "success": true,
  "data": {},
  "meta": {
    "page": 1,
    "limit": 20,
    "totalItems": 142,
    "totalPages": 8,
    "hasNextPage": true,
    "hasPreviousPage": false
  },
  "message": "Operation completed successfully"
}
```

### 7.3. Standard Error Envelope (RFC 7807 Compliant)
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "The provided payload failed schema validation.",
    "status": 422,
    "details": [
      {
        "field": "items[0].quantity",
        "issue": "Quantity must be greater than 0"
      }
    ],
    "timestamp": "2026-08-19T22:45:00.000Z",
    "path": "/api/v1/sales/checkout"
  }
}
```

### 7.4. Standard Query Parameters for Angular 21 Data Tables
- `page`: Integer (1-based, default: `1`)
- `limit`: Integer (default: `20`, max: `100`)
- `sort`: Field and direction format (e.g., `createdAt,desc`, `sellingPrice,asc`)
- `search`: Global search term across indexed textual columns (SKU, name, barcode)
- `filter[{field}]`: Exact or range filters (e.g., `filter[status]=ACTIVE`, `filter[dateFrom]=2026-08-01`)

---

## 8. Testing & Quality Assurance

Before declaring any backend feature complete:

1. **Unit Tests (JUnit 5 + Mockito):**
   - 100% test coverage on Domain entities, Value Objects, Tax calculations, and Weighted Average Cost math.
2. **Integration Tests (Testcontainers with MySQL 8):**
   - Test transactions, rollbacks, and pessimistic locks using real containerized MySQL instances.
   - Concurrency tests: Simulate simultaneous checkout requests on the last unit of stock.
3. **API Contract Tests (MockMvc / WebTestClient):**
   - Ensure response schemas strictly match the TypeScript models defined in Angular 21 (`boxy-fe/src/app/core/models`).
4. **Security Audits:**
   - Verify that unauthorized branch manipulation attempts return HTTP 403 Forbidden.

---

## 9. Development Workflow

Follow this strict step-by-step workflow:

1. **Analyze Domain & Existing FE Contracts:** Check `boxy-fe/src/app/features` and `src/app/core/models`.
2. **Design / Update Database Schema:** Write Flyway migration scripts (`db/migration/V{n}__{description}.sql`) with MySQL InnoDB syntax, foreign keys, and indexes.
3. **Implement Domain Entities & Invariants:** Pure business rules and Value Objects.
4. **Implement Spring Data JPA Repository Layer:** Queries with `@Lock(LockModeType.PESSIMISTIC_WRITE)` for inventory.
5. **Implement Application Use Cases (Command/Query):** Orchestrate business logic and domain events.
6. **Implement API Controller & Validation DTOs:** Match Angular 21 HTTP payloads and RFC 7807 error responses.
7. **Write Integration & Concurrency Tests:** Validate against race conditions and boundary values using Testcontainers.
8. **Verify & Review:** Verify against `GEMINI.md` and document endpoints via OpenAPI/Swagger.
