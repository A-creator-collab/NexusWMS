# NEXUS WMS
### Intelligent Warehouse Management System

**A modern, multi-tenant warehouse management platform designed to streamline inventory operations, order fulfillment, and warehouse traceability.**

NEXUS WMS is an enterprise-oriented warehouse management system built to manage inventory movement and operational workflows through a structured, secure, and auditable platform.

The system combines a responsive web interface, a modular backend, transactional inventory management, and a mobile-friendly scanning experience to support warehouse operations from receiving goods to dispatching shipments.

## ✨ Key Features

### 📦 Inventory Management
- Track inventory balances and stock movements.
- Maintain an inventory ledger for operational traceability.
- Support receiving, transfers, cycle counts, and returns.
- Apply FIFO and FEFO allocation strategies.
- Track lots and serial numbers where supported.

### 🚚 Warehouse Operations
- Record and process incoming inventory.
- Allocate inventory for order fulfillment.
- Support scan-confirmed picking workflows.
- Manage shipment dispatch.
- Track transfers, partial receipts, and inventory adjustments.
- Handle returns and disposition workflows.

### 🏢 Multi-Tenant Architecture
- Support organization and warehouse-specific access scopes.
- Apply role-based access control.
- Restrict operations according to permissions.
- Maintain audit records for important operations.

### 📱 Mobile-Friendly Workflows
- Responsive interfaces for desktop and mobile devices.
- Progressive Web App (PWA) support for warehouse workflows.
- Scan-oriented picking and inventory operations.
- Localization support, including right-to-left interfaces.

### 🔐 Reliability and Security
- Database-backed sessions.
- Scoped authorization.
- Transactional inventory operations.
- Idempotent commands to reduce duplicate processing.
- Structured error handling and request correlation.
- Audit and outbox records for supported operations.

### 🌐 Enterprise Web Interface
- Modern dashboard and inventory views.
- Global search.
- Keyboard-accessible command palette.
- Light and dark themes.
- Multilingual interface support.

## 🛠️ Technology Stack

| Layer | Technologies |
|---|---|
| Frontend | Next.js, React, TypeScript |
| Backend | NestJS, Node.js, TypeScript |
| Database | PostgreSQL |
| ORM | Prisma |
| Caching and infrastructure | Redis |
| Object storage infrastructure | MinIO |
| API documentation | OpenAPI |
| Monorepo management | pnpm Workspaces, Turborepo |
| Testing | Vitest, Playwright |
| Deployment infrastructure | Docker Compose |

## 🏗️ Architecture

NEXUS WMS follows a modular monorepo architecture.

```text
                    NEXUS WMS
                        |
          +-------------+-------------+
          |                           |
     Web Application             REST API
     Next.js / React             NestJS
          |                           |
          |                    +------+------+
          |                    |             |
          |                 Domain        Prisma
          |                 Logic           |
          |                                 |
          +--------------------------- PostgreSQL
                                           |
                                   Inventory Ledger
                                           |
                              Audit / Outbox Records

          Supporting Infrastructure
             Redis | MinIO | Docker
```

### Core Components

- **Web Application:** Provides the responsive user interface for warehouse operators and administrators.
- **REST API:** Handles requests, authorization, and operational workflows.
- **Domain Layer:** Encapsulates inventory rules, allocation logic, quantity handling, and permission checks.
- **Database Layer:** Uses PostgreSQL and Prisma for persistent data and transactional operations.
- **Supporting Services:** Redis and MinIO provide infrastructure for supported application needs.

## 🔄 How It Works

A typical inventory fulfillment workflow follows these steps:

1. **Receive:** Record incoming goods.
2. **Update Inventory:** Persist the corresponding inventory changes.
3. **Allocate:** Select suitable inventory for an order using the configured allocation strategy.
4. **Pick:** Confirm the required items through the picking workflow.
5. **Dispatch:** Process the shipment using the supported dispatch operation.
6. **Audit:** Maintain relevant inventory and operational records.

The platform also supports workflows for stock transfers, cycle counts, returns, and lot or serial traceability.

## 🚀 Getting Started

### Prerequisites

Install the following tools before running the project:

- Node.js 24 LTS
- pnpm 11
- Docker Desktop or Docker Engine
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/A-creator-collab/NexusWMS.git
```

### 2. Navigate to the Project

```bash
cd NexusWMS
```

### 3. Configure Environment Variables

Copy the example environment files:

```bash
cp .env.example .env
cp .env.example apps/api/.env
```

On Windows, you can also copy these files manually using File Explorer.

Review the environment variables and update any settings required for your local setup.

### 4. Install Dependencies

```bash
pnpm install
```

### 5. Start Supporting Services

```bash
docker compose up -d postgres redis minio
```

### 6. Generate the Database Client

```bash
pnpm db:generate
```

### 7. Apply Database Migrations

```bash
pnpm db:migrate
```

### 8. Seed Development Data

```bash
pnpm db:seed
```

Run the seed command only in an appropriate development environment. Review its reset behavior before using it against any existing database.

### 9. Build the Project

```bash
pnpm build
```

### 10. Start the Applications

Open two separate terminals from the project root.

**Terminal 1 — Backend API**

```bash
pnpm --filter @nexus/api start
```

**Terminal 2 — Frontend**

```bash
pnpm --filter @nexus/web start
```

Open the application at:

`http://localhost:3000/en/login`

For the development demo account, consult the repository's seed configuration and documentation.

## 🧪 Testing and Verification

Run the available quality checks from the project root:

```bash
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

To run end-to-end acceptance tests:

```bash
pnpm db:seed
pnpm test:e2e
```

Ensure the required infrastructure and application processes are running before executing the end-to-end tests.

These checks help validate type safety, code quality, automated tests, build integrity, and supported operational workflows.

## 🔌 Development Endpoints

| Service | Local Address |
|---|---|
| Web Application | `http://localhost:3000` |
| API | `http://localhost:4000/api/v1` |
| OpenAPI Documentation | `http://localhost:4000/api/docs` |
| OpenAPI JSON | `http://localhost:4000/api/docs-json` |
| PostgreSQL | `localhost:5434` |
| Redis | `localhost:6381` |
| MinIO API | `localhost:9002` |
| MinIO Console | `http://localhost:9003` |

These addresses reflect the repository's documented local development configuration.

## 🛣️ Future Roadmap

Potential areas for further development include:

- AI-assisted demand forecasting.
- Intelligent replenishment recommendations.
- Agent-based warehouse operations.
- Voice-controlled inventory commands.
- Natural-language product search.
- Predictive stockout detection.
- Advanced warehouse analytics.
- Expanded offline scanning and synchronization.
- Integration with external commerce and logistics systems.

These capabilities are proposed extensions, not claims that they are already implemented.

## 📌 Project Scope

NEXUS WMS provides a production-oriented core for warehouse management and a demonstration deployment.

Not every enterprise capability is complete. Refer to `docs/FEATURE_MATRIX.md` for the implementation status of individual features.

Optional integrations and advanced capabilities should be treated according to their documented implementation status and release requirements.

## 🤝 Contributing

Contributions that improve reliability, usability, documentation, testing, or warehouse workflows are welcome.

A typical contribution workflow is:

1. Fork the repository.
2. Create a feature branch.
3. Implement and test your changes.
4. Submit a pull request with a clear description.

Please follow the repository's existing development conventions.

## 📄 License

This project includes a license file in the repository. Refer to `LICENSE` for the applicable terms.

## 👨‍💻 Author

**Atharv Singh**

GitHub: [A-creator-collab](https://github.com/A-creator-collab)

Project: [NEXUS WMS](https://github.com/A-creator-collab/NexusWMS)

---

**NEXUS WMS — Bringing structure, traceability, and reliability to warehouse operations.**
